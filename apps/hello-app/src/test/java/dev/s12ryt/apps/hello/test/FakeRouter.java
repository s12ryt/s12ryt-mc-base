package dev.s12ryt.apps.hello.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.s12ryt.mcapp.api.AppRouter;

/**
 * 測試用 Router：記錄所有註冊的路由，並提供 lookup 查找 handler。
 */
public class FakeRouter implements AppRouter {

    public record RouteEntry(String method, String pattern, AppHandler handler) {}

    private final List<RouteEntry> routes = new ArrayList<>();

    @Override
    public void get(String pattern, AppHandler handler) {
        routes.add(new RouteEntry("GET", pattern, handler));
    }

    @Override
    public void post(String pattern, AppHandler handler) {
        routes.add(new RouteEntry("POST", pattern, handler));
    }

    @Override
    public void put(String pattern, AppHandler handler) {
        routes.add(new RouteEntry("PUT", pattern, handler));
    }

    @Override
    public void delete(String pattern, AppHandler handler) {
        routes.add(new RouteEntry("DELETE", pattern, handler));
    }

    public List<RouteEntry> routes() {
        return Collections.unmodifiableList(routes);
    }

    public AppHandler lookup(String method, String pattern) {
        return routes.stream()
                .filter(r -> r.method().equals(method) && r.pattern().equals(pattern))
                .map(RouteEntry::handler)
                .findFirst()
                .orElse(null);
    }
}
