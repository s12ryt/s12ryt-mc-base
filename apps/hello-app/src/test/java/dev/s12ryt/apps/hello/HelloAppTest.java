package dev.s12ryt.apps.hello;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.s12ryt.apps.hello.test.FakeAppContext;
import dev.s12ryt.apps.hello.test.FakeAppContext.FakeLogger;
import dev.s12ryt.apps.hello.test.FakeAppContext.FakeScheduler;
import dev.s12ryt.apps.hello.test.FakeAppContext.InMemoryStorage;
import dev.s12ryt.apps.hello.test.FakeRouter;
import dev.s12ryt.mcapp.api.AppHttpRequest;
import dev.s12ryt.mcapp.api.AppHttpResponse;
import dev.s12ryt.mcapp.api.AppSpec;
import dev.s12ryt.mcapp.api.AppRouter.AppHandler;
import dev.s12ryt.mcapp.api.EmptyAppConfig;

/**
 * HelloApp TDD：驗證示範 App 的完整行為。
 */
@DisplayName("HelloApp 示範 App")
class HelloAppTest {

    private HelloApp app;
    private FakeAppContext ctx;
    private FakeRouter router;
    private FakeLogger logger;
    private FakeScheduler scheduler;
    private InMemoryStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        app = new HelloApp();
        router = new FakeRouter();
        logger = new FakeLogger();
        scheduler = new FakeScheduler();
        storage = new InMemoryStorage();
        AppSpec spec = new AppSpec("hello-app", "Hello App", "1.0.0", "dev.s12ryt.apps.hello.HelloApp");
        ctx = new FakeAppContext(spec, router, storage, logger, scheduler,
                Path.of("."), new EmptyAppConfig());
    }

    // ─── 生命週期 ───

    @Nested
    @DisplayName("生命週期")
    class Lifecycle {

        @Test
        @DisplayName("onEnable 註冊路由")
        void onEnableRegistersRoutes() throws Exception {
            app.onEnable(ctx);
            assertFalse(router.routes().isEmpty(), "應至少註冊一條路由");
        }

        @Test
        @DisplayName("onEnable 記錄啟動日誌")
        void onEnableLogsStartup() throws Exception {
            app.onEnable(ctx);
            assertTrue(logger.lines().stream().anyMatch(l -> l.contains("started") || l.contains("HelloApp")),
                    "應記錄啟動日誌");
        }

        @Test
        @DisplayName("onEnable 啟動 heartbeat 排程任務")
        void onEnableStartsHeartbeat() throws Exception {
            app.onEnable(ctx);
            assertTrue(scheduler.scheduledTasks.size() > 0,
                    "應至少有一個排程任務");
        }

        @Test
        @DisplayName("onEnable 建立 visits 表")
        void onEnableCreatesVisitsTable() throws Exception {
            app.onEnable(ctx);
            try (Connection conn = storage.connection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) as c FROM visits")) {
                rs.next();
                assertEquals(1, rs.getInt("c"), "visits 表應存在且初始為 1 行（含初始計數行）");
            }
        }

        @Test
        @DisplayName("onDisable 記錄停止日誌")
        void onDisableLogsShutdown() throws Exception {
            app.onEnable(ctx);
            app.onDisable();
            assertTrue(logger.lines().stream().anyMatch(l -> l.contains("stop") || l.contains("disable") || l.contains("shut")),
                    "應記錄停止日誌");
        }
    }

    // ─── 路由：GET /ping ───

    @Nested
    @DisplayName("GET /ping")
    class PingRoute {

        @Test
        @DisplayName("回傳 pong JSON")
        void pingReturnsPong() throws Exception {
            app.onEnable(ctx);
            AppHandler handler = router.lookup("GET", "/ping");
            assertNotNull(handler, "應註冊 GET /ping 路由");
            AppHttpResponse response = handler.handle(buildGet("/apps/hello-app/ping"));
            assertEquals(200, response.status());
            String body = new String(response.body(), StandardCharsets.UTF_8);
            assertTrue(body.contains("pong"), "body 應包含 pong");
        }
    }

    // ─── 路由：GET /info ───

    @Nested
    @DisplayName("GET /info")
    class InfoRoute {

        @Test
        @DisplayName("回傳 App 資訊")
        void infoReturnsAppInfo() throws Exception {
            app.onEnable(ctx);
            AppHandler handler = router.lookup("GET", "/info");
            assertNotNull(handler, "應註冊 GET /info 路由");
            AppHttpResponse response = handler.handle(buildGet("/apps/hello-app/info"));
            assertEquals(200, response.status());
            String body = new String(response.body(), StandardCharsets.UTF_8);
            assertTrue(body.contains("hello-app"), "body 應包含 app id");
            assertTrue(body.contains("1.0.0"), "body 應包含 version");
        }
    }

    // ─── 路由：POST /echo ───

    @Nested
    @DisplayName("POST /echo")
    class EchoRoute {

        @Test
        @DisplayName("回傳 body")
        void echoReturnsBody() throws Exception {
            app.onEnable(ctx);
            AppHandler handler = router.lookup("POST", "/echo");
            assertNotNull(handler, "應註冊 POST /echo 路由");
            AppHttpResponse response = handler.handle(buildPost("/apps/hello-app/echo", "hello world"));
            assertEquals(200, response.status());
            String body = new String(response.body(), StandardCharsets.UTF_8);
            assertTrue(body.contains("hello world"), "body 應包含 echo 的內容");
        }
    }

    // ─── 路由：GET /count ───

    @Nested
    @DisplayName("GET /count")
    class CountRoute {

        @Test
        @DisplayName("初始計數為 0")
        void countInitiallyZero() throws Exception {
            app.onEnable(ctx);
            AppHandler handler = router.lookup("GET", "/count");
            assertNotNull(handler, "應註冊 GET /count 路由");
            AppHttpResponse response = handler.handle(buildGet("/apps/hello-app/count"));
            assertEquals(200, response.status());
            String body = new String(response.body(), StandardCharsets.UTF_8);
            assertTrue(body.contains("0"), "body 應包含計數 0");
        }
    }

    // ─── 路由：POST /count/increment ───

    @Nested
    @DisplayName("POST /count/increment")
    class IncrementRoute {

        @Test
        @DisplayName("increment 後計數 +1")
        void incrementIncreasesCount() throws Exception {
            app.onEnable(ctx);
            AppHandler incHandler = router.lookup("POST", "/count/increment");
            assertNotNull(incHandler, "應註冊 POST /count/increment 路由");
            AppHttpResponse incResponse = incHandler.handle(buildPost("/apps/hello-app/count/increment", ""));
            assertEquals(200, incResponse.status());

            // 驗證計數變為 1
            AppHandler countHandler = router.lookup("GET", "/count");
            AppHttpResponse countResponse = countHandler.handle(buildGet("/apps/hello-app/count"));
            String body = new String(countResponse.body(), StandardCharsets.UTF_8);
            assertTrue(body.contains("1"), "increment 後計數應為 1");
        }
    }

    // ─── Helper ───

    private AppHttpRequest buildGet(String path) {
        return new AppHttpRequest("GET", path, Map.of(), Map.of(), new byte[0]);
    }

    private AppHttpRequest buildPost(String path, String body) {
        return new AppHttpRequest("POST", path, Map.of(), Map.of(),
                body.getBytes(StandardCharsets.UTF_8));
    }
}
