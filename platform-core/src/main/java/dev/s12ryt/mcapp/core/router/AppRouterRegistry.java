package dev.s12ryt.mcapp.core.router;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.s12ryt.mcapp.api.AppRouter;

/**
 * App 路由註冊表。
 *
 * <p>為每個 App 提供一個 {@link AppRouter} 實例，讓 App 註冊路由（get/post/put/delete）。
 * 路由 pattern 以 / 開頭，實際路徑前綴為 /apps/{appId}。
 * App 卸載時呼叫 {@link #clear(String)} 清除路由。
 *
 * <p>本類別不依賴 Javalin，可獨立單元測試。Javalin 整合層透過 {@link #match(String, String)}
 * 查詢路由再分派。
 */
public final class AppRouterRegistry {

    /** 單一路由條目。 */
    public record RouteEntry(String appId, String method, String pattern, AppRouter.AppHandler handler) {}

    /** 路由匹配結果。 */
    public record RouteMatch(String appId, AppRouter.AppHandler handler, Map<String, String> pathParams) {}

    /** /apps/{appId} 前綴。 */
    private static final String APPS_PREFIX = "/apps/";

    private final ConcurrentHashMap<String, List<RouteEntryImpl>> routesByApp = new ConcurrentHashMap<>();

    /** 為指定 App 建立一個 AppRouter，供 App 註冊路由。 */
    public AppRouter createRouter(String appId) {
        routesByApp.computeIfAbsent(appId, k -> Collections.synchronizedList(new ArrayList<>()));
        return new AppRouterImpl(appId);
        }

    /** 取得指定 App 的所有已註冊路由（唯讀快照）。 */
    public List<RouteEntry> routes(String appId) {
        List<RouteEntryImpl> list = routesByApp.get(appId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf(list.stream().map(e -> new RouteEntry(e.appId, e.method, e.pattern, e.handler)).toList());
        }
    }

    /**
     * 根據 HTTP method 與完整路徑匹配路由。
     *
     * @param method HTTP 方法（不分大小寫）
     * @param path   完整路徑，例如 /apps/hello-app/ping
     * @return 匹配結果，包含 appId、handler 與路徑參數；無匹配回 empty
     */
    public Optional<RouteMatch> match(String method, String path) {
        if (method == null || path == null || !path.startsWith(APPS_PREFIX)) {
            return Optional.empty();
        }

        // 剝離 /apps/ 前綴 → {appId}/{rest...}
        String afterPrefix = path.substring(APPS_PREFIX.length());
        int slash = afterPrefix.indexOf('/');
        if (slash < 0) {
            // 只有 /apps/{appId} 沒有子路徑
            return Optional.empty();
        }
        String appId = afterPrefix.substring(0, slash);
        String subPath = afterPrefix.substring(slash); // 以 / 開頭

        String methodUpper = method.toUpperCase(java.util.Locale.ROOT);

        List<RouteEntryImpl> list = routesByApp.get(appId);
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }

        synchronized (list) {
            for (RouteEntryImpl entry : list) {
                if (!entry.method.equals(methodUpper)) {
                    continue;
                }
                Matcher matcher = entry.compiledPattern.matcher(subPath);
                if (matcher.matches()) {
                    Map<String, String> params = extractParams(matcher, entry);
                    return Optional.of(new RouteMatch(appId, entry.handler, params));
                }
            }
        }

        return Optional.empty();
    }

    /** 清除指定 App 的所有路由。 */
    public void clear(String appId) {
        List<RouteEntryImpl> list = routesByApp.remove(appId);
        if (list != null) {
            synchronized (list) {
                list.clear();
            }
        }
    }

    /** 清除所有 App 的路由。 */
    public void clearAll() {
        for (String appId : List.copyOf(routesByApp.keySet())) {
            clear(appId);
        }
    }

    // ─────────────────── 內部 ───────────────────

    private Map<String, String> extractParams(Matcher matcher, RouteEntryImpl entry) {
        if (entry.paramNames.isEmpty()) {
            return Map.of();
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (String name : entry.paramNames) {
            params.put(name, matcher.group(name));
        }
        return Map.copyOf(params);
    }

    /** 將 pattern（如 /items/{id}）轉為正則表達式，並記錄參數名。 */
    private static Pattern compilePattern(String pattern, List<String> paramNames) {
        // 將 {name} 替換為 (?<name>[^/]+)，並對其他正則特殊字元跳脫
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '{') {
                int close = pattern.indexOf('}', i);
                if (close < 0) {
                    // 未閉合的 { — 當字面處理
                    regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));
                    i++;
                    continue;
                }
                String name = pattern.substring(i + 1, close);
                // Java 正則具名組名僅允許 [A-Za-z][A-Za-z0-9]*（不允許底線）
                if (!name.matches("[A-Za-z][A-Za-z0-9]*")) {
                    throw new IllegalArgumentException(
                            "路由參數名無效: {" + name + "}（須為字母開頭，僅含字母/數字）");
                }
                if (paramNames.contains(name)) {
                    throw new IllegalArgumentException(
                            "路由參數名重複: {" + name + "}（pattern: " + pattern + "）");
                }
                paramNames.add(name);
                regex.append("(?<").append(name).append(">[^/]+)");
                i = close + 1;
            } else if (isRegexSpecial(c)) {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));
                i++;
            } else {
                regex.append(c);
                i++;
            }
        }
        return Pattern.compile(regex.toString());
    }

    private static boolean isRegexSpecial(char c) {
        return ".[](){}*+?^$|\\".indexOf(c) >= 0;
    }

    /** 內部路由條目（含編譯後的正則與參數名）。 */
    private static final class RouteEntryImpl {
        final String appId;
        final String method;
        final String pattern;
        final AppRouter.AppHandler handler;
        final Pattern compiledPattern;
        final List<String> paramNames;

        RouteEntryImpl(String appId, String method, String pattern, AppRouter.AppHandler handler) {
            this.appId = appId;
            this.method = method;
            this.pattern = pattern;
            this.handler = handler;
            this.paramNames = new ArrayList<>();
            this.compiledPattern = compilePattern(pattern, this.paramNames);
        }
    }

    /** AppRouter 實作：註冊路由到 registry。 */
    private final class AppRouterImpl implements AppRouter {

        private final String appId;

        AppRouterImpl(String appId) {
            this.appId = appId;
        }

        @Override
        public void get(String pattern, AppHandler handler) {
            register("GET", pattern, handler);
        }

        @Override
        public void post(String pattern, AppHandler handler) {
            register("POST", pattern, handler);
        }

        @Override
        public void put(String pattern, AppHandler handler) {
            register("PUT", pattern, handler);
        }

        @Override
        public void delete(String pattern, AppHandler handler) {
            register("DELETE", pattern, handler);
        }

        private void register(String method, String pattern, AppHandler handler) {
            if (pattern == null || pattern.isEmpty()) {
                throw new IllegalArgumentException("pattern 不可為空");
            }
            if (handler == null) {
                throw new IllegalArgumentException("handler 不可為 null");
            }
            List<RouteEntryImpl> list = routesByApp.get(appId);
            if (list == null) {
                // App 被 clear 後 router 仍持有 appId，重新建 list
                list = routesByApp.computeIfAbsent(appId, k -> Collections.synchronizedList(new ArrayList<>()));
            }
            synchronized (list) {
                list.add(new RouteEntryImpl(appId, method, pattern, handler));
            }
        }
    }
}
