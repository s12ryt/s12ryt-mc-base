package dev.s12ryt.mcapp.core.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.s12ryt.mcapp.core.bootstrap.PlatformBootstrap;
import io.javalin.testtools.JavalinTest;

/**
 * WebServer 整合測試。
 *
 * <p>使用 JavalinTest 啟動真實伺服器，驗證完整的 HTTP 請求/回應流程。
 * 測試涵蓋：登入、token 保護、App 管理 API、日誌 API、App 路由分派。
 *
 * <p>Javalin 6 TestClient API：
 * <ul>
 *   <li>{@code client.get(path)} — 回傳 {@link io.javalin.testtools.Response}，有 {@code code()} 和 {@code body().string()}</li>
 *   <li>{@code client.get(path, req -> req.header(name, value))} — 第二參為 {@code Consumer<Request.Builder>} 設定 header</li>
 *   <li>{@code client.post(path, object)} — object 被 JSON 序列化為 body</li>
 *   <li>{@code client.post(path, object, req -> ...)} — 帶 header 的 POST</li>
 * </ul>
 */
@DisplayName("WebServer 整合測試")
class WebServerTest {

    @TempDir
    Path tmp;

    private PlatformBootstrap bootstrap;
    private WebServer webServer;

    @BeforeEach
    void setUp() {
        bootstrap = new PlatformBootstrap(tmp);
        bootstrap.startup();
        webServer = new WebServer(bootstrap);
    }

    @AfterEach
    void tearDown() {
        if (webServer != null) {
            webServer.close();
        }
        if (bootstrap != null) {
            bootstrap.shutdown();
        }
    }

    // ─── Health Check ──────────────────────────────────────

    @Nested
    @DisplayName("健康檢查")
    class HealthCheck {

        @Test
        @DisplayName("GET /api/health 回 200 + status ok")
        void healthCheck_returns200() {
            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.get("/api/health");
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).contains("ok");
            });
        }
    }

    // ─── Auth ──────────────────────────────────────────────

    @Nested
    @DisplayName("認證 API")
    class AuthApi {

        @Test
        @DisplayName("POST /api/auth/login 正確密碼 → 200 + token")
        void login_withCorrectPassword_returns200AndToken() {
            String password = bootstrap.getAuthManager().currentPassword();

            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.post("/api/auth/login", Map.of("password", password));
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).contains("token");
            });
        }

        @Test
        @DisplayName("POST /api/auth/login 錯誤密碼 → 401")
        void login_withWrongPassword_returns401() {
            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.post("/api/auth/login", Map.of("password", "wrong-password"));
                assertThat(response.code()).isEqualTo(401);
            });
        }

        @Test
        @DisplayName("GET /api/apps 無 token → 401")
        void protectedEndpoint_withoutToken_returns401() {
            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.get("/api/apps");
                assertThat(response.code()).isEqualTo(401);
            });
        }

        @Test
        @DisplayName("GET /api/apps 帶有效 token → 200")
        void protectedEndpoint_withValidToken_returns200() {
            String password = bootstrap.getAuthManager().currentPassword();
            String token = bootstrap.getAuthManager().login(password).orElseThrow();

            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.get("/api/apps", req -> req.header("Authorization", "Bearer " + token));
                assertThat(response.code()).isEqualTo(200);
            });
        }

        @Test
        @DisplayName("POST /api/auth/logout 有效 token → 200")
        void logout_withValidToken_returns200() {
            String password = bootstrap.getAuthManager().currentPassword();
            String token = bootstrap.getAuthManager().login(password).orElseThrow();

            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.post("/api/auth/logout", null,
                        req -> req.header("Authorization", "Bearer " + token));
                assertThat(response.code()).isEqualTo(200);
            });
        }

        @Test
        @DisplayName("POST /api/auth/logout 後 token 失效 → 401")
        void logout_invalidatesToken() {
            String password = bootstrap.getAuthManager().currentPassword();
            String token = bootstrap.getAuthManager().login(password).orElseThrow();

            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                // 先 logout
                client.post("/api/auth/logout", null,
                        req -> req.header("Authorization", "Bearer " + token));
                // 再用同一 token 存取保護 API
                var response = client.get("/api/apps",
                        req -> req.header("Authorization", "Bearer " + token));
                assertThat(response.code()).isEqualTo(401);
            });
        }
    }

    // ─── App Management API ────────────────────────────────

    @Nested
    @DisplayName("App 管理 API")
    class AppManagementApi {

        @Test
        @DisplayName("GET /api/apps 列出 App（空列表）")
        void listApps_returnsEmptyArray() {
            String password = bootstrap.getAuthManager().currentPassword();
            String token = bootstrap.getAuthManager().login(password).orElseThrow();

            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.get("/api/apps",
                        req -> req.header("Authorization", "Bearer " + token));
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).contains("[]");
            });
        }

        @Test
        @DisplayName("POST /api/apps/unknown-id/unload 未知 App → 404")
        void unloadUnknownApp_returns404() {
            String password = bootstrap.getAuthManager().currentPassword();
            String token = bootstrap.getAuthManager().login(password).orElseThrow();

            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.post("/api/apps/unknown-id/unload", null,
                        req -> req.header("Authorization", "Bearer " + token));
                assertThat(response.code()).isEqualTo(404);
            });
        }

        @Test
        @DisplayName("POST /api/apps/unknown-id/reload 未知 App → 404")
        void reloadUnknownApp_returns404() {
            String password = bootstrap.getAuthManager().currentPassword();
            String token = bootstrap.getAuthManager().login(password).orElseThrow();

            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.post("/api/apps/unknown-id/reload", null,
                        req -> req.header("Authorization", "Bearer " + token));
                assertThat(response.code()).isEqualTo(404);
            });
        }

        @Test
        @DisplayName("GET /api/apps/unknown-id/logs 未知 App → 404")
        void logsUnknownApp_returns404() {
            String password = bootstrap.getAuthManager().currentPassword();
            String token = bootstrap.getAuthManager().login(password).orElseThrow();

            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.get("/api/apps/unknown-id/logs",
                        req -> req.header("Authorization", "Bearer " + token));
                assertThat(response.code()).isEqualTo(404);
            });
        }
    }

    // ─── App Route Dispatch ────────────────────────────────

    @Nested
    @DisplayName("App 路由分派")
    class AppRouteDispatch {

        @Test
        @DisplayName("GET /apps/nonexistent-app/... → 404")
        void appRoute_nonexistentApp_returns404() {
            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.get("/apps/nonexistent-app/ping");
                assertThat(response.code()).isEqualTo(404);
            });
        }

        @Test
        @DisplayName("已註冊路由成功分派 → 200 + handler 回應（不被 404 覆蓋）")
        void appRoute_registeredHandler_returnsHandlerResponse() {
            var registry = bootstrap.getRouterRegistry();
            var router = registry.createRouter("test-app");
            router.get("/ping", req -> dev.s12ryt.mcapp.api.AppHttpResponse.json("{\"pong\":true}"));

            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.get("/apps/test-app/ping");
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).contains("pong");
            });
        }

        @Test
        @DisplayName("已註冊路由但 method 不符 → 404")
        void appRoute_methodMismatch_returns404() {
            var registry = bootstrap.getRouterRegistry();
            var router = registry.createRouter("method-app");
            router.get("/only-get", req -> dev.s12ryt.mcapp.api.AppHttpResponse.text("hi"));

            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.post("/apps/method-app/only-get", null);
                assertThat(response.code()).isEqualTo(404);
            });
        }

        @Test
        @DisplayName("handler 拋例外 → 500")
        void appRoute_handlerThrows_returns500() {
            var registry = bootstrap.getRouterRegistry();
            var router = registry.createRouter("boom-app");
            router.get("/boom", req -> {
                throw new IllegalStateException("boom-test");
            });

            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.get("/apps/boom-app/boom");
                assertThat(response.code()).isEqualTo(500);
                assertThat(response.body().string()).contains("boom-test");
            });
        }

        @Test
        @DisplayName("路徑參數傳遞：handler 收到的 path 與 query 含 pathParams")
        void appRoute_pathParams_passedToHandler() {
            var registry = bootstrap.getRouterRegistry();
            var router = registry.createRouter("params-app");
            router.get("/items/{id}", req -> {
                // pathParams 會被併入 query map（鍵名同 {name}）
                String id = req.queryParam("id").orElse("MISSING");
                return dev.s12ryt.mcapp.api.AppHttpResponse.text("id=" + id);
            });

            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.get("/apps/params-app/items/42");
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("id=42");
            });
        }

        @Test
        @DisplayName("二進位 body 完整傳遞（不被 UTF-8 轉換損壞）")
        void appRoute_binaryBody_passedIntact() {
            var registry = bootstrap.getRouterRegistry();
            var router = registry.createRouter("binary-app");
            router.post("/upload", req -> {
                // 回顯 body 長度：raw binary 傳遞應為 6 bytes
                byte[] body = req.body();
                return dev.s12ryt.mcapp.api.AppHttpResponse.text("len=" + body.length);
            });

            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                // 模擬二進位資料：0x00 0x01 0xFF 0xE4 0xB8 0xAD
                byte[] binary = new byte[]{0x00, 0x01, (byte) 0xFF, (byte) 0xE4, (byte) 0xB8, (byte) 0xAD};
                // 用 raw RequestBody 避免 testtools 的 JSON 序列化
                var response = client.request("/apps/binary-app/upload", reqBuilder ->
                        reqBuilder.post(okhttp3.RequestBody.create(binary, okhttp3.MediaType.get("application/octet-stream"))));
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("len=6");
            });
        }
    }

    // ─── Web Console 靜態檔案 ───────────────────────────────

    @Nested
    @DisplayName("Web 控制台靜態檔案")
    class WebConsoleStatic {

        @Test
        @DisplayName("GET /console/ 無資源時回 web console not built（開發環境）")
        void consoleRoot_withoutBuiltAssets_returnsNotFoundOrOk() {
            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.get("/console/");
                // 測試環境沒有 web-console dist 資源 → 404 "web console not built"
                // （若 processResources 已打包則為 200 index.html）
                assertThat(response.code()).isIn(200, 404);
            });
        }

        @Test
        @DisplayName("路徑遍歷攻擊 /console/../plugin.yml 回 404，不洩漏 classpath 資源")
        void pathTraversal_returns404_andNeverLeaksResources() {
            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.get("/console/../plugin.yml");
                assertThat(response.code()).isEqualTo(404);
                // 回應 body 不得包含 plugin.yml 的內容（如 name= 或 main=）
                var body = response.body().string();
                assertThat(body).doesNotContain("s12ryt-mc-base");
                assertThat(body).doesNotContain("S12rytPlugin");
            });
        }

        @Test
        @DisplayName("編碼路徑遍歷 /console/%2e%2e/plugin.yml 回 404")
        void encodedPathTraversal_returns404() {
            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                var response = client.get("/console/%2e%2e/plugin.yml");
                assertThat(response.code()).isEqualTo(404);
            });
        }

        @Test
        @DisplayName("GET /console（無尾斜線）redirect 到 /console/")
        void consoleWithoutTrailingSlash_redirectsToConsoleSlash() throws Exception {
            JavalinTest.test(webServer.getJavalin(), (server, client) -> {
                // testtools client 自動跟隨 redirect，改用不跟隨的 OkHttpClient 驗證 302
                var noRedirect = new okhttp3.OkHttpClient.Builder().followRedirects(false).build();
                var request = new okhttp3.Request.Builder()
                        .url("http://localhost:" + server.port() + "/console")
                        .get().build();
                try (var response = noRedirect.newCall(request).execute()) {
                    assertThat(response.code()).isEqualTo(302);
                    assertThat(response.header("Location")).isEqualTo("/console/");
                }
            });
        }
    }
}
