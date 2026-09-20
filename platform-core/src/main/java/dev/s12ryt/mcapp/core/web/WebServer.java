package dev.s12ryt.mcapp.core.web;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.s12ryt.mcapp.api.AppHttpRequest;
import dev.s12ryt.mcapp.api.AppHttpResponse;
import dev.s12ryt.mcapp.api.AppLogger;
import dev.s12ryt.mcapp.core.app.AppContainer;
import dev.s12ryt.mcapp.core.app.AppContainer.AppHandle;
import dev.s12ryt.mcapp.core.auth.AuthManager;
import dev.s12ryt.mcapp.core.bootstrap.PlatformBootstrap;
import dev.s12ryt.mcapp.core.router.AppRouterRegistry;
import dev.s12ryt.mcapp.core.router.AppRouterRegistry.RouteMatch;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;

/**
 * 內嵌 Javalin 6 Web 伺服器。
 *
 * <p>提供平台管理 API 與 App 路由分派：
 * <ul>
 *   <li>GET  /api/health — 健康檢查</li>
 *   <li>POST /api/auth/login — 密碼登入，回傳 token</li>
 *   <li>POST /api/auth/logout — 登出（需 token）</li>
 *   <li>POST /api/auth/change-password — 變更密碼（需 token）</li>
 *   <li>GET  /api/apps — 列出所有 App（需 token）</li>
 *   <li>POST /api/apps/{id}/reload — 重載 App（需 token）</li>
 *   <li>POST /api/apps/{id}/unload — 卸載 App（需 token）</li>
 *   <li>GET  /api/apps/{id}/logs — 取得 App 日誌（需 token）</li>
 *   <li>ALL  /apps/{appId}/... — 分派到 App 註冊的路由</li>
 * </ul>
 *
 * <p>受保護的 API 需要 Authorization: Bearer {token} 標頭。
 */
public final class WebServer implements AutoCloseable {



    private final PlatformBootstrap bootstrap;
    private final Javalin app;

    /**
     * 建構子：建立 Javalin app 並配置所有路由（不自動 start）。
     *
     * @param bootstrap 平台生命週期管理器
     */
    public WebServer(PlatformBootstrap bootstrap) {
        this.bootstrap = java.util.Objects.requireNonNull(bootstrap, "bootstrap");
        this.app = Javalin.create();
        configureRoutes();
    }

    /** 取得 Javalin 實例（測試用 JavalinTest）。 */
    public Javalin getJavalin() {
        return app;
    }

    /**
     * 啟動伺服器。
     *
     * @param port 埠號（0 = 隨機）
     */
    public void start(int port) {
        app.start(port);
    }

    /** 停止伺服器。 */
    public void stop() {
        app.stop();
    }

    @Override
    public void close() {
        app.stop();
    }

    // ─── 路由配置 ──────────────────────────────────────────

    private void configureRoutes() {
        // 健康檢查（無需認證）
        app.get("/api/health", ctx -> {
            ctx.json(Map.of("status", "ok"));
        });

        // 認證 API（無需認證）
        app.post("/api/auth/login", this::handleLogin);

        // 受保護的 API
        app.before("/api/*", ctx -> {
            // /api/health 和 /api/auth/login 不需要認證
            String path = ctx.path();
            if (path.equals("/api/health") || path.equals("/api/auth/login")) {
                return;
            }
            requireAuth(ctx);
        });

        app.post("/api/auth/logout", this::handleLogout);
        app.post("/api/auth/change-password", this::handleChangePassword);
        app.get("/api/apps", this::handleListApps);
        app.post("/api/apps/{appId}/reload", this::handleReloadApp);
        app.post("/api/apps/{appId}/unload", this::handleUnloadApp);
        app.get("/api/apps/{appId}/logs", this::handleAppLogs);

        // App 路由分派：所有 /apps/{appId}/... 請求
        app.before("/apps/{appId}/*", ctx -> {
            String method = ctx.method().name();
            String path = ctx.path();

            Optional<RouteMatch> match = bootstrap.getRouterRegistry().match(method, path);
            if (match.isEmpty()) {
                // 無匹配路由：不攔截，讓 Javalin 繼續（最終回 404）
                return;
            }

            RouteMatch routeMatch = match.get();
            AppHttpRequest request = buildAppHttpRequest(ctx, routeMatch.pathParams());
            try {
                AppHttpResponse response = routeMatch.handler().handle(request);
                writeAppHttpResponse(ctx, response);
                // 關鍵：分派完成後跳過剩餘 handler，
                // 否則 Javalin 找不到 endpoint handler 會以 404 覆蓋已寫入的回應
                ctx.skipRemainingHandlers();
            } catch (Exception e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .json(Map.of("error", e.getMessage() != null ? e.getMessage() : e.toString()));
                ctx.skipRemainingHandlers();
            }
        });

        // Web 控制台靜態檔案服務：/console/* → web-console/ classpath 資源
        app.get("/console/*", ctx -> serveWebConsole(ctx));
        // /console（無尾斜線）也重導到 /console/（Javalin pattern /console/* 不匹配 /console 本身）
        app.get("/console", ctx -> ctx.redirect("/console/"));
        // 根路徑重導到控制台
        app.get("/", ctx -> ctx.redirect("/console/"));
    }

    // ─── Web 控制台靜態檔案 ─────────────────────────────────

    private void serveWebConsole(Context ctx) {
        String path = ctx.path().substring("/console/".length());
        // 路徑遍歷防禦：拒絕含 ".." 的路徑（防止讀取 classpath 任意資源，如 /console/../plugin.yml）
        if (path.contains("..")) {
            ctx.status(HttpStatus.NOT_FOUND).result("not found: " + path);
            return;
        }
        if (path.isEmpty() || path.endsWith("/")) {
            path = "index.html";
        }
        String resourcePath = "web-console/" + path;
        InputStream resource = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (resource == null) {
            // 帶副檔名的缺失資源（如 /console/assets/missing.js）→ 404，不做 SPA fallback
            if (path.contains(".")) {
                ctx.status(HttpStatus.NOT_FOUND).result("not found: " + path);
                return;
            }
            // SPA fallback：無副檔名路徑回傳 index.html（Vue Router 接管）
            resource = getClass().getClassLoader().getResourceAsStream("web-console/index.html");
            if (resource == null) {
                ctx.status(HttpStatus.NOT_FOUND).result("web console not built");
                return;
            }
            // fallback 時 path 已不是原檔名，Content-Type 強制為 html
            ctx.header("Content-Type", "text/html; charset=UTF-8");
            try {
                ctx.result(resource.readAllBytes());
            } catch (Exception e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("failed to serve web console: " + e.getMessage());
            }
            return;
        }
        try {
            byte[] bytes = resource.readAllBytes();
            ctx.result(bytes);
            // 設定 Content-Type
            if (path.endsWith(".html")) {
                ctx.header("Content-Type", "text/html; charset=UTF-8");
            } else if (path.endsWith(".js")) {
                ctx.header("Content-Type", "application/javascript; charset=UTF-8");
            } else if (path.endsWith(".css")) {
                ctx.header("Content-Type", "text/css; charset=UTF-8");
            } else if (path.endsWith(".json")) {
                ctx.header("Content-Type", "application/json; charset=UTF-8");
            } else if (path.endsWith(".svg")) {
                ctx.header("Content-Type", "image/svg+xml");
            } else if (path.endsWith(".ico")) {
                ctx.header("Content-Type", "image/x-icon");
            }
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("failed to serve web console: " + e.getMessage());
        }
    }

    // ─── 認證 ──────────────────────────────────────────────

    private void requireAuth(Context ctx) {
        String auth = ctx.header("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            // 只拋 UnauthorizedResponse（Javalin 統一處理 401 回應），避免重複回應設定
            throw new UnauthorizedResponse();
        }
        String token = auth.substring(7);
        AuthManager authManager = bootstrap.getAuthManager();
        if (authManager == null || !authManager.isValid(token)) {
            throw new UnauthorizedResponse();
        }
        ctx.attribute("token", token);
    }

    // ─── API Handlers ──────────────────────────────────────

    private void handleLogin(Context ctx) {
        AuthManager auth = bootstrap.getAuthManager();
        if (auth == null) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "auth not initialized"));
            return;
        }

        try {
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            String password = body.get("password").getAsString();

            Optional<String> token = auth.login(password);
            if (token.isPresent()) {
                ctx.json(Map.of("token", token.get()));
            } else {
                ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "invalid password"));
            }
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid request body"));
        }
    }

    private void handleLogout(Context ctx) {
        String token = ctx.attribute("token");
        AuthManager auth = bootstrap.getAuthManager();
        if (auth != null && token != null) {
            auth.logout(token);
        }
        ctx.json(Map.of("status", "ok"));
    }

    private void handleChangePassword(Context ctx) {
        AuthManager auth = bootstrap.getAuthManager();
        if (auth == null) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "auth not initialized"));
            return;
        }

        try {
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            String oldPassword = body.get("oldPassword").getAsString();
            String newPassword = body.get("newPassword").getAsString();

            auth.changePassword(oldPassword, newPassword);
            ctx.json(Map.of("status", "ok"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid request body"));
        }
    }

    private void handleListApps(Context ctx) {
        AppContainer container = bootstrap.getAppContainer();
        if (container == null) {
            ctx.json(List.of());
            return;
        }
        List<Map<String, Object>> apps = container.list().stream()
                .map(h -> Map.<String, Object>of(
                        "id", h.id(),
                        "name", h.name(),
                        "version", h.version(),
                        "state", h.state().name(),
                        "error", h.error()))
                .toList();
        ctx.json(apps);
    }

    private void handleReloadApp(Context ctx) {
        String appId = ctx.pathParam("appId");
        AppContainer container = bootstrap.getAppContainer();
        if (container == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "container not initialized"));
            return;
        }

        Optional<AppHandle> handle = container.reload(appId);
        if (handle.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "app not found: " + appId));
            return;
        }
        AppHandle h = handle.get();
        ctx.json(Map.of(
                "id", h.id(),
                "name", h.name(),
                "version", h.version(),
                "state", h.state().name(),
                "error", h.error()));
    }

    private void handleUnloadApp(Context ctx) {
        String appId = ctx.pathParam("appId");
        AppContainer container = bootstrap.getAppContainer();
        if (container == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "container not initialized"));
            return;
        }

        boolean unloaded = container.unload(appId);
        if (!unloaded) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "app not found: " + appId));
            return;
        }
        ctx.json(Map.of("status", "ok", "id", appId));
    }

    private void handleAppLogs(Context ctx) {
        String appId = ctx.pathParam("appId");
        AppContainer container = bootstrap.getAppContainer();
        if (container == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "container not initialized"));
            return;
        }

        Optional<AppHandle> handle = container.get(appId);
        if (handle.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "app not found: " + appId));
            return;
        }

        // 取得 App 的 logger（透過 PlatformAppServices 的 context 追蹤）
        // 目前 logger 由 PlatformAppServices 管理，需要一個方式取得
        // 暫時回空列表（後續由 PlatformAppServices.getLogger(appId) 提供）
        var services = bootstrap.getAppServices();
        if (services != null) {
            AppLogger logger = services.getLogger(appId);
            if (logger != null) {
                int maxLines = 200;
                String maxParam = ctx.queryParam("maxLines");
                if (maxParam != null) {
                    try {
                        maxLines = Integer.parseInt(maxParam);
                    } catch (NumberFormatException ignored) {
                    }
                }
                List<String> lines = logger.recentLines(maxLines);
                ctx.json(Map.of("appId", appId, "lines", lines));
                return;
            }
        }
        ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "logger not found for app: " + appId));
    }

    // ─── App 路由分派 helper ────────────────────────────────

    private AppHttpRequest buildAppHttpRequest(Context ctx, Map<String, String> pathParams) {
        String method = ctx.method().name();
        String path = ctx.path();
        Map<String, List<String>> headers = new java.util.LinkedHashMap<>();
        java.util.Collections.list(ctx.req().getHeaderNames()).forEach(name -> {
            String value = ctx.header(name);
            if (value != null) {
                headers.put(name.toLowerCase(java.util.Locale.ROOT), List.of(value));
            }
        });
        Map<String, List<String>> query = new java.util.LinkedHashMap<>();
        ctx.req().getParameterMap().forEach((k, v) -> query.put(k, List.of(v)));
        // 路徑參數併入 query（鍵名同 {name}），讓 App 可透過 queryParam() 取得
        if (pathParams != null) {
            pathParams.forEach((k, v) -> query.put(k, List.of(v)));
        }
        byte[] raw = ctx.bodyAsBytes();
        byte[] body = raw != null ? raw : new byte[0];
        return new AppHttpRequest(method, path, headers, query, body);
    }

    private void writeAppHttpResponse(Context ctx, AppHttpResponse response) {
        ctx.status(response.status());
        response.headers().forEach(ctx::header);
        if (response.body() != null && response.body().length > 0) {
            ctx.result(response.body());
        }
    }
}
