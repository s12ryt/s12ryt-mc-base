package dev.s12ryt.mcapp.core.router;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.s12ryt.mcapp.api.AppHttpRequest;
import dev.s12ryt.mcapp.api.AppHttpResponse;
import dev.s12ryt.mcapp.api.AppRouter;

/**
 * AppRouterRegistry 的行為契約測試。
 *
 * <p>不涉及 Javalin，只驗證路由註冊、查詢、匹配、清除。
 */
class AppRouterRegistryTest {

    private AppRouterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AppRouterRegistry();
    }

    // ─────────────────── 路由註冊 ───────────────────

    @Nested
    @DisplayName("createRouter + 註冊路由")
    class RouteRegistration {

        @Test
        @DisplayName("createRouter 回傳非 null AppRouter")
        void createRouterReturnsNonNull() {
            AppRouter router = registry.createRouter("hello-app");
            assertNotNull(router);
        }

        @Test
        @DisplayName("註冊單一 GET 路由後 routes() 回傳一個條目")
        void registerSingleGetRoute() {
            AppRouter router = registry.createRouter("hello-app");
            router.get("/ping", req -> AppHttpResponse.text("pong"));

            List<AppRouterRegistry.RouteEntry> routes = registry.routes("hello-app");
            assertEquals(1, routes.size());
            assertEquals("hello-app", routes.get(0).appId());
            assertEquals("GET", routes.get(0).method());
            assertEquals("/ping", routes.get(0).pattern());
            assertNotNull(routes.get(0).handler());
        }

        @Test
        @DisplayName("多個 HTTP 方法各自獨立註冊")
        void registerMultipleMethods() {
            AppRouter router = registry.createRouter("api-app");
            router.get("/items", req -> AppHttpResponse.ok(new byte[0], "application/json"));
            router.post("/items", req -> AppHttpResponse.status(201));
            router.put("/items/1", req -> AppHttpResponse.status(204));
            router.delete("/items/1", req -> AppHttpResponse.status(204));

            List<AppRouterRegistry.RouteEntry> routes = registry.routes("api-app");
            assertEquals(4, routes.size());
            assertEquals("GET", routes.get(0).method());
            assertEquals("POST", routes.get(1).method());
            assertEquals("PUT", routes.get(2).method());
            assertEquals("DELETE", routes.get(3).method());
        }

        @Test
        @DisplayName("多個 App 的路由互不干擾")
        void multipleAppsRoutesAreIsolated() {
            AppRouter routerA = registry.createRouter("app-a");
            routerA.get("/ping", req -> AppHttpResponse.text("a"));

            AppRouter routerB = registry.createRouter("app-b");
            routerB.get("/ping", req -> AppHttpResponse.text("b"));

            assertEquals(1, registry.routes("app-a").size());
            assertEquals(1, registry.routes("app-b").size());
        }

        @Test
        @DisplayName("未知 appId 的 routes() 回傳空 list")
        void routesForUnknownAppReturnsEmpty() {
            assertTrue(registry.routes("nope").isEmpty());
        }
    }

    // ─────────────────── 路由匹配 ───────────────────

    @Nested
    @DisplayName("match(method, path)")
    class RouteMatching {

        @Test
        @DisplayName("精確路徑匹配")
        void exactPathMatch() {
            AppRouter router = registry.createRouter("hello-app");
            router.get("/ping", req -> AppHttpResponse.text("pong"));

            Optional<AppRouterRegistry.RouteMatch> match = registry.match("GET", "/apps/hello-app/ping");
            assertTrue(match.isPresent());
            assertEquals("hello-app", match.get().appId());
        }

        @Test
        @DisplayName("路徑參數匹配 {param}")
        void pathParamMatch() {
            AppRouter router = registry.createRouter("api-app");
            router.get("/items/{id}", req -> AppHttpResponse.text(req.queryParam("id").orElse("?")));

            Optional<AppRouterRegistry.RouteMatch> match = registry.match("GET", "/apps/api-app/items/42");
            assertTrue(match.isPresent());
            assertEquals("api-app", match.get().appId());
            assertEquals("42", match.get().pathParams().get("id"));
        }

        @Test
        @DisplayName("多個路徑參數")
        void multiplePathParams() {
            AppRouter router = registry.createRouter("api-app");
            router.get("/users/{userId}/items/{itemId}", req -> AppHttpResponse.status(200));

            Optional<AppRouterRegistry.RouteMatch> match =
                    registry.match("GET", "/apps/api-app/users/alice/items/99");
            assertTrue(match.isPresent());
            assertEquals("alice", match.get().pathParams().get("userId"));
            assertEquals("99", match.get().pathParams().get("itemId"));
        }

        @Test
        @DisplayName("HTTP 方法不同不匹配")
        void methodMismatch() {
            AppRouter router = registry.createRouter("hello-app");
            router.get("/ping", req -> AppHttpResponse.text("pong"));

            assertTrue(registry.match("POST", "/apps/hello-app/ping").isEmpty());
        }

        @Test
        @DisplayName("路徑不存在回傳 empty")
        void noMatchReturnsEmpty() {
            AppRouter router = registry.createRouter("hello-app");
            router.get("/ping", req -> AppHttpResponse.text("pong"));

            assertTrue(registry.match("GET", "/apps/hello-app/missing").isEmpty());
        }

        @Test
        @DisplayName("appId 前綴不正確不匹配")
        void wrongAppIdPrefix() {
            AppRouter router = registry.createRouter("hello-app");
            router.get("/ping", req -> AppHttpResponse.text("pong"));

            // /apps/other-app/ping 不應匹配 hello-app 的路由
            assertTrue(registry.match("GET", "/apps/other-app/ping").isEmpty());
        }

        @Test
        @DisplayName("match 不分大小寫方法名")
        void matchCaseInsensitiveMethod() {
            AppRouter router = registry.createRouter("hello-app");
            router.get("/ping", req -> AppHttpResponse.text("pong"));

            assertTrue(registry.match("get", "/apps/hello-app/ping").isPresent());
        }

        @Test
        @DisplayName("pattern 以 / 結尾但請求路徑沒有 → 不匹配")
        void trailingSlashMismatch() {
            AppRouter router = registry.createRouter("hello-app");
            router.get("/ping", req -> AppHttpResponse.text("pong"));

            // /apps/hello-app/ping/ 不應匹配 /ping
            assertTrue(registry.match("GET", "/apps/hello-app/ping/").isEmpty());
        }
    }

    // ─────────────────── 路由清除 ───────────────────

    @Nested
    @DisplayName("clear / clearAll")
    class RouteClearing {

        @Test
        @DisplayName("clear 移除指定 App 的所有路由")
        void clearRemovesAppRoutes() {
            AppRouter routerA = registry.createRouter("app-a");
            routerA.get("/ping", req -> AppHttpResponse.text("a"));
            routerA.get("/pong", req -> AppHttpResponse.text("a"));

            AppRouter routerB = registry.createRouter("app-b");
            routerB.get("/ping", req -> AppHttpResponse.text("b"));

            registry.clear("app-a");

            assertTrue(registry.routes("app-a").isEmpty());
            assertEquals(1, registry.routes("app-b").size());
        }

        @Test
        @DisplayName("clear 未知 appId 不拋例外")
        void clearUnknownAppNoException() {
            assertDoesNotThrow(() -> registry.clear("nope"));
        }

        @Test
        @DisplayName("clearAll 移除所有 App 路由")
        void clearAllRemovesEverything() {
            AppRouter routerA = registry.createRouter("app-a");
            routerA.get("/ping", req -> AppHttpResponse.text("a"));

            AppRouter routerB = registry.createRouter("app-b");
            routerB.get("/ping", req -> AppHttpResponse.text("b"));

            registry.clearAll();

            assertTrue(registry.routes("app-a").isEmpty());
            assertTrue(registry.routes("app-b").isEmpty());
        }

        @Test
        @DisplayName("clear 後再註冊路由可正常匹配")
        void reRegisterAfterClear() {
            AppRouter router = registry.createRouter("hello-app");
            router.get("/ping", req -> AppHttpResponse.text("pong"));

            registry.clear("hello-app");

            // clear 後再用同一個 router 註冊仍可匹配
            router.get("/ping", req -> AppHttpResponse.text("pong2"));

            Optional<AppRouterRegistry.RouteMatch> match = registry.match("GET", "/apps/hello-app/ping");
            assertTrue(match.isPresent());
        }
    }

    // ─────────────────── handler 執行 ───────────────────

    @Nested
    @DisplayName("match 後的 handler 可執行")
    class HandlerExecution {

        @Test
        @DisplayName("handler 收到的 AppHttpRequest 含正確 method 和 path")
        void handlerReceivesCorrectRequest() throws Exception {
            AppRouter router = registry.createRouter("echo-app");
            router.post("/echo", req -> AppHttpResponse.text(req.method() + " " + req.path()));

            Optional<AppRouterRegistry.RouteMatch> match = registry.match("POST", "/apps/echo-app/echo");
            assertTrue(match.isPresent());

            AppHttpRequest request = new AppHttpRequest(
                    "POST", "/apps/echo-app/echo", Map.of(), Map.of(), new byte[0]);
            AppHttpResponse response = match.get().handler().handle(request);

            assertEquals(200, response.status());
            String bodyText = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals("POST /apps/echo-app/echo", bodyText);
        }
    }

    // ─────────────────── pattern 參數驗證 ───────────────────

    @Nested
    @DisplayName("pattern {name} 驗證（非法名稱拋 IllegalArgumentException）")
    class PatternValidation {

        @Test
        @DisplayName("空參數名 {} 拋 IllegalArgumentException")
        void emptyParamName_throwsIllegalArgument() {
            AppRouter router = registry.createRouter("valid-app");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> router.get("/items/{}", req -> AppHttpResponse.text("x")));
            assertTrue(ex.getMessage().contains("參數名"));
        }

        @Test
        @DisplayName("數字開頭參數名 {1abc} 拋 IllegalArgumentException")
        void digitLeadingParamName_throwsIllegalArgument() {
            AppRouter router = registry.createRouter("valid-app");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> router.get("/items/{1abc}", req -> AppHttpResponse.text("x")));
            assertTrue(ex.getMessage().contains("參數名"));
        }

        @Test
        @DisplayName("含空格參數名 {a b} 拋 IllegalArgumentException")
        void spaceInParamName_throwsIllegalArgument() {
            AppRouter router = registry.createRouter("valid-app");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> router.get("/items/{a b}", req -> AppHttpResponse.text("x")));
            assertTrue(ex.getMessage().contains("參數名"));
        }

        @Test
        @DisplayName("重複參數名 /items/{id}/x/{id} 拋 IllegalArgumentException")
        void duplicateParamName_throwsIllegalArgument() {
            AppRouter router = registry.createRouter("valid-app");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> router.get("/items/{id}/x/{id}", req -> AppHttpResponse.text("x")));
            assertTrue(ex.getMessage().contains("重複"));
        }

        @Test
        @DisplayName("未閉合 { 當字面處理（不拋例外，既有語意）")
        void unclosedBrace_treatedAsLiteral() {
            AppRouter router = registry.createRouter("valid-app");
            assertDoesNotThrow(() -> router.get("/items/{open", req -> AppHttpResponse.text("x")));
        }

        @Test
        @DisplayName("含底線參數名 {id_2} 拋 IllegalArgumentException（Java 正則組名不允許底線）")
        void underscoreParamName_throwsIllegalArgument() {
            AppRouter router = registry.createRouter("valid-app");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> router.get("/items/{id_2}", req -> AppHttpResponse.text("x")));
            assertTrue(ex.getMessage().contains("參數名"));
        }

        @Test
        @DisplayName("合法參數名（字母開頭/數字在後）可正常註冊與匹配")
        void validParamNames_registerAndMatch() {
            AppRouter router = registry.createRouter("valid-app");
            assertDoesNotThrow(() -> router.get("/items/{aId}/{id2}", req -> AppHttpResponse.text("x")));
            Optional<AppRouterRegistry.RouteMatch> match = registry.match("GET", "/apps/valid-app/items/7/8");
            assertTrue(match.isPresent());
            assertEquals(Map.of("aId", "7", "id2", "8"), match.get().pathParams());
        }
    }
}
