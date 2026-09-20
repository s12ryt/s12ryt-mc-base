package dev.s12ryt.mcapp.core.app;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.s12ryt.mcapp.api.AppContext;
import dev.s12ryt.mcapp.api.AppRouter;
import dev.s12ryt.mcapp.api.AppSpec;
import dev.s12ryt.mcapp.api.AppStorage;
import dev.s12ryt.mcapp.core.router.AppRouterRegistry;

/**
 * PlatformAppServices 的整合測試。
 *
 * <p>使用真實依賴元件（AppRouterRegistry、AppStorageImpl、AppLoggerImpl、AppSchedulerImpl、
 * AppConfigImpl、AppContextImpl），驗證 createContext→destroyContext 的完整生命週期。
 */
@DisplayName("PlatformAppServices")
class PlatformAppServicesTest {

    @TempDir
    Path tmp;

    private Path appsDir;
    private Path logDir;
    private AppRouterRegistry registry;
    private PlatformAppServices services;

    @BeforeEach
    void setUp() throws IOException {
        appsDir = tmp.resolve("apps");
        logDir = tmp.resolve("logs");
        Files.createDirectories(appsDir);
        Files.createDirectories(logDir);
        registry = new AppRouterRegistry();
        services = new PlatformAppServices(appsDir, logDir, registry);
    }

    @AfterEach
    void tearDown() {
        // destroyContext 已在測試中呼叫；但若某些測試遺漏，這裡不強制清理
    }

    private AppSpec spec(String id) {
        return new AppSpec(id, "Test App " + id, "1.0.0", "dev.s12ryt.mcapp.core.app.testapps.RecordingApp");
    }

    // ─────────────────── Construction ───────────────────

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("implements AppServices")
        void implementsAppServices() {
            assertInstanceOf(AppServices.class, services);
        }

        @Test
        @DisplayName("null appsDirectory 拋 NPE")
        void nullAppsDir() {
            assertThrows(NullPointerException.class,
                    () -> new PlatformAppServices(null, logDir, registry));
        }

        @Test
        @DisplayName("null logDir 拋 NPE")
        void nullLogDir() {
            assertThrows(NullPointerException.class,
                    () -> new PlatformAppServices(appsDir, null, registry));
        }

        @Test
        @DisplayName("null registry 拋 NPE")
        void nullRegistry() {
            assertThrows(NullPointerException.class,
                    () -> new PlatformAppServices(appsDir, logDir, null));
        }
    }

    // ─────────────────── createContext ───────────────────

    @Nested
    @DisplayName("createContext")
    class CreateContext {

        @Test
        @DisplayName("回傳非 null 的 AppContext")
        void returnsNonNullContext() {
            AppSpec spec = spec("my-app");
            AppContext ctx = services.createContext(spec, getClass().getClassLoader());
            assertNotNull(ctx);
            services.destroyContext(spec);
        }

        @Test
        @DisplayName("context 的 spec 與傳入的一致")
        void contextSpecMatches() {
            AppSpec spec = spec("hello-app");
            AppContext ctx = services.createContext(spec, getClass().getClassLoader());
            assertEquals("hello-app", ctx.spec().id());
            assertEquals("Test App hello-app", ctx.spec().name());
            assertEquals("1.0.0", ctx.spec().version());
            services.destroyContext(spec);
        }

        @Test
        @DisplayName("context 的 router 非 null")
        void contextRouterNotNull() {
            AppSpec spec = spec("app1");
            AppContext ctx = services.createContext(spec, getClass().getClassLoader());
            assertNotNull(ctx.router());
            services.destroyContext(spec);
        }

        @Test
        @DisplayName("context 的 storage 非 null 且可取得連線")
        void contextStorageWorks() throws SQLException {
            AppSpec spec = spec("app2");
            AppContext ctx = services.createContext(spec, getClass().getClassLoader());
            AppStorage storage = ctx.storage();
            assertNotNull(storage);
            // 確認可以取得連線並建表
            try (var conn = storage.connection(); var stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INTEGER)");
                stmt.execute("INSERT INTO test VALUES (1)");
            }
            services.destroyContext(spec);
        }

        @Test
        @DisplayName("context 的 logger 非 null 且可寫入")
        void contextLoggerWorks() {
            AppSpec spec = spec("app3");
            AppContext ctx = services.createContext(spec, getClass().getClassLoader());
            ctx.logger().info("hello from test");
            assertFalse(ctx.logger().recentLines(10).isEmpty());
            services.destroyContext(spec);
        }

        @Test
        @DisplayName("context 的 scheduler 非 null")
        void contextSchedulerNotNull() {
            AppSpec spec = spec("app4");
            AppContext ctx = services.createContext(spec, getClass().getClassLoader());
            assertNotNull(ctx.scheduler());
            services.destroyContext(spec);
        }

        @Test
        @DisplayName("context 的 dataDirectory 非 null 且已存在")
        void contextDataDirectoryExists() throws IOException {
            AppSpec spec = spec("app5");
            AppContext ctx = services.createContext(spec, getClass().getClassLoader());
            Path dataDir = ctx.dataDirectory();
            assertNotNull(dataDir);
            assertTrue(Files.exists(dataDir));
            assertTrue(Files.isDirectory(dataDir));
            services.destroyContext(spec);
        }

        @Test
        @DisplayName("context 的 config 非 null")
        void contextConfigNotNull() {
            AppSpec spec = spec("app6");
            AppContext ctx = services.createContext(spec, getClass().getClassLoader());
            assertNotNull(ctx.config());
            services.destroyContext(spec);
        }

        @Test
        @DisplayName("router 已註冊到 registry（createRouter 已被呼叫）")
        void routerRegisteredInRegistry() {
            AppSpec spec = spec("app7");
            AppContext ctx = services.createContext(spec, getClass().getClassLoader());
            // 註冊一條路由
            ctx.router().get("/ping", req -> dev.s12ryt.mcapp.api.AppHttpResponse.text("pong"));
            // 透過 registry 查詢
            Optional<AppRouterRegistry.RouteMatch> match = registry.match("GET", "/apps/app7/ping");
            assertTrue(match.isPresent());
            assertEquals("app7", match.get().appId());
            services.destroyContext(spec);
        }

        @Test
        @DisplayName("dataDirectory 路徑為 apps/{appId}/data")
        void dataDirectoryPath() {
            AppSpec spec = spec("app8");
            AppContext ctx = services.createContext(spec, getClass().getClassLoader());
            Path expected = appsDir.resolve("app8").resolve("data");
            assertEquals(expected, ctx.dataDirectory());
            services.destroyContext(spec);
        }

        @Test
        @DisplayName("null spec 拋 NPE")
        void nullSpec() {
            assertThrows(NullPointerException.class,
                    () -> services.createContext(null, getClass().getClassLoader()));
        }

        @Test
        @DisplayName("null classLoader 拋 NPE")
        void nullClassLoader() {
            AppSpec spec = spec("app9");
            assertThrows(NullPointerException.class,
                    () -> services.createContext(spec, null));
        }
    }

    // ─────────────────── destroyContext ───────────────────

    @Nested
    @DisplayName("destroyContext")
    class DestroyContext {

        @Test
        @DisplayName("destroyContext 後 router 被清除")
        void destroysRouter() {
            AppSpec spec = spec("dest-app1");
            AppContext ctx = services.createContext(spec, getClass().getClassLoader());
            ctx.router().get("/ping", req -> dev.s12ryt.mcapp.api.AppHttpResponse.text("pong"));
            // 確認路由存在
            assertTrue(registry.match("GET", "/apps/dest-app1/ping").isPresent());
            // 銷毀
            services.destroyContext(spec);
            // 路由應被清除
            assertFalse(registry.match("GET", "/apps/dest-app1/ping").isPresent());
        }

        @Test
        @DisplayName("destroyContext 後 storage 拋 IllegalStateException")
        void destroysStorage() {
            AppSpec spec = spec("dest-app2");
            AppContext ctx = services.createContext(spec, getClass().getClassLoader());
            services.destroyContext(spec);
            // storage 應已關閉
            assertThrows(IllegalStateException.class, () -> ctx.storage().connection());
        }

        @Test
        @DisplayName("destroyContext 後 logger 不接受新寫入")
        void destroysLogger() {
            AppSpec spec = spec("dest-app3");
            AppContext ctx = services.createContext(spec, getClass().getClassLoader());
            services.destroyContext(spec);
            // logger close 後新寫入被拒（ring buffer 不變）
            int before = ctx.logger().recentLines(100).size();
            ctx.logger().info("should be ignored");
            assertEquals(before, ctx.logger().recentLines(100).size());
        }

        @Test
        @DisplayName("destroyContext 後 scheduler 拋 RejectedExecutionException")
        void destroysScheduler() {
            AppSpec spec = spec("dest-app4");
            AppContext ctx = services.createContext(spec, getClass().getClassLoader());
            services.destroyContext(spec);
            // scheduler 應已關閉
            assertThrows(java.util.concurrent.RejectedExecutionException.class,
                    () -> ctx.scheduler().runAsync(() -> {}));
        }

        @Test
        @DisplayName("destroyContext 不拋例外（未知 spec）")
        void destroyUnknownSpecNoThrow() {
            AppSpec unknown = spec("nonexistent");
            // 不應拋例外
            assertDoesNotThrow(() -> services.destroyContext(unknown));
        }

        @Test
        @DisplayName("destroyContext 冪等（重複呼叫不拋）")
        void destroyIdempotent() {
            AppSpec spec = spec("dest-app5");
            services.createContext(spec, getClass().getClassLoader());
            services.destroyContext(spec);
            // 第二次不拋
            assertDoesNotThrow(() -> services.destroyContext(spec));
        }

        @Test
        @DisplayName("null spec 拋 NPE")
        void nullSpec() {
            assertThrows(NullPointerException.class,
                    () -> services.destroyContext(null));
        }
    }

    // ─────────────────── 整合生命週期 ───────────────────

    @Nested
    @DisplayName("Integration Lifecycle")
    class IntegrationLifecycle {

        @Test
        @DisplayName("create→destroy→create 同一 appId 可重複")
        void createDestroyCreate() {
            AppSpec spec = spec("lifecycle-app");
            // 第一次
            AppContext ctx1 = services.createContext(spec, getClass().getClassLoader());
            assertNotNull(ctx1);
            ctx1.logger().info("first cycle");
            services.destroyContext(spec);

            // 第二次 — 應建立全新 context
            AppContext ctx2 = services.createContext(spec, getClass().getClassLoader());
            assertNotNull(ctx2);
            // 新 context 的 logger ring buffer 應為空（全新實例）
            assertTrue(ctx2.logger().recentLines(10).isEmpty());
            services.destroyContext(spec);
        }

        @Test
        @DisplayName("多個 App 同時存在，各自獨立")
        void multipleAppsIndependent() {
            AppSpec specA = spec("app-a");
            AppSpec specB = spec("app-b");

            AppContext ctxA = services.createContext(specA, getClass().getClassLoader());
            AppContext ctxB = services.createContext(specB, getClass().getClassLoader());

            // A 寫日誌
            ctxA.logger().info("from A");
            // B 寫日誌
            ctxB.logger().info("from B");

            // A 的日誌只有 A 的訊息
            assertTrue(ctxA.logger().recentLines(10).stream()
                    .anyMatch(l -> l.contains("from A")));
            assertFalse(ctxA.logger().recentLines(10).stream()
                    .anyMatch(l -> l.contains("from B")));

            // B 的日誌只有 B 的訊息
            assertTrue(ctxB.logger().recentLines(10).stream()
                    .anyMatch(l -> l.contains("from B")));
            assertFalse(ctxB.logger().recentLines(10).stream()
                    .anyMatch(l -> l.contains("from A")));

            // 銷毀 A 不影響 B
            services.destroyContext(specA);
            // B 的 storage 仍存在（未被關閉）
            assertNotNull(ctxB.storage());
            services.destroyContext(specB);
        }

        @Test
        @DisplayName("destroy A 後 A 的路由被清除，B 的路由保留")
        void destroyClearsOnlyTargetApp() {
            AppSpec specA = spec("route-a");
            AppSpec specB = spec("route-b");

            AppContext ctxA = services.createContext(specA, getClass().getClassLoader());
            AppContext ctxB = services.createContext(specB, getClass().getClassLoader());

            ctxA.router().get("/a", req -> dev.s12ryt.mcapp.api.AppHttpResponse.text("a"));
            ctxB.router().get("/b", req -> dev.s12ryt.mcapp.api.AppHttpResponse.text("b"));

            assertTrue(registry.match("GET", "/apps/route-a/a").isPresent());
            assertTrue(registry.match("GET", "/apps/route-b/b").isPresent());

            // 銷毀 A
            services.destroyContext(specA);

            // A 的路由清除
            assertFalse(registry.match("GET", "/apps/route-a/a").isPresent());
            // B 的路由保留
            assertTrue(registry.match("GET", "/apps/route-b/b").isPresent());

            services.destroyContext(specB);
        }
    }
}
