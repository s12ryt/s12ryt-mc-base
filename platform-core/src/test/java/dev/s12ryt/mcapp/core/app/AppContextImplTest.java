package dev.s12ryt.mcapp.core.app;

import dev.s12ryt.mcapp.api.AppConfig;
import dev.s12ryt.mcapp.api.AppContext;
import dev.s12ryt.mcapp.api.AppRouter;
import dev.s12ryt.mcapp.api.AppSpec;
import dev.s12ryt.mcapp.api.AppStorage;
import dev.s12ryt.mcapp.api.AppLogger;
import dev.s12ryt.mcapp.api.AppScheduler;
import dev.s12ryt.mcapp.core.config.AppConfigImpl;
import dev.s12ryt.mcapp.core.logger.AppLoggerImpl;
import dev.s12ryt.mcapp.core.router.AppRouterRegistry;
import dev.s12ryt.mcapp.core.scheduler.AppSchedulerImpl;
import dev.s12ryt.mcapp.core.storage.AppStorageImpl;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AppContextImpl")
class AppContextImplTest {

    @TempDir
    Path tmp;

    private AppSpec spec;
    private AppRouterRegistry registry;
    private AppStorageImpl storage;
    private AppLoggerImpl logger;
    private AppSchedulerImpl scheduler;
    private Path dataDirectory;
    private AppConfigImpl config;
    private AppContextImpl context;

    @BeforeEach
    void setUp() {
        spec = new AppSpec("test-app", "Test App", "1.0.0", "dev.s12ryt.test.TestApp");
        registry = new AppRouterRegistry();
        AppRouter router = registry.createRouter("test-app");
        Path appsDir = tmp.resolve("apps");
        storage = new AppStorageImpl(appsDir, "test-app");
        logger = new AppLoggerImpl("test-app", tmp.resolve("logs"));
        scheduler = new AppSchedulerImpl("test-app");
        dataDirectory = appsDir.resolve("test-app").resolve("data");
        try {
            Files.createDirectories(dataDirectory);
        } catch (Exception e) {
            fail("無法建立 dataDirectory", e);
        }
        config = new AppConfigImpl(new Properties());
        context = new AppContextImpl(spec, router, storage, logger, scheduler, dataDirectory, config);
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
        logger.close();
        storage.close();
    }

    @Nested
    @DisplayName("Construction")
    class Construction {
        @Test
        @DisplayName("implements AppContext")
        void implementsAppContext() {
            assertInstanceOf(AppContext.class, context);
        }

        @Test
        @DisplayName("null spec 拋 NPE")
        void nullSpecThrows() {
            assertThrows(NullPointerException.class, () ->
                new AppContextImpl(null, registry.createRouter("x"), storage, logger, scheduler, dataDirectory, config));
        }

        @Test
        @DisplayName("null router 拋 NPE")
        void nullRouterThrows() {
            assertThrows(NullPointerException.class, () ->
                new AppContextImpl(spec, null, storage, logger, scheduler, dataDirectory, config));
        }

        @Test
        @DisplayName("null storage 拋 NPE")
        void nullStorageThrows() {
            assertThrows(NullPointerException.class, () ->
                new AppContextImpl(spec, registry.createRouter("x"), null, logger, scheduler, dataDirectory, config));
        }

        @Test
        @DisplayName("null logger 拋 NPE")
        void nullLoggerThrows() {
            assertThrows(NullPointerException.class, () ->
                new AppContextImpl(spec, registry.createRouter("x"), storage, null, scheduler, dataDirectory, config));
        }

        @Test
        @DisplayName("null scheduler 拋 NPE")
        void nullSchedulerThrows() {
            assertThrows(NullPointerException.class, () ->
                new AppContextImpl(spec, registry.createRouter("x"), storage, logger, null, dataDirectory, config));
        }

        @Test
        @DisplayName("null dataDirectory 拋 NPE")
        void nullDataDirectoryThrows() {
            assertThrows(NullPointerException.class, () ->
                new AppContextImpl(spec, registry.createRouter("x"), storage, logger, scheduler, null, config));
        }

        @Test
        @DisplayName("null config 拋 NPE")
        void nullConfigThrows() {
            assertThrows(NullPointerException.class, () ->
                new AppContextImpl(spec, registry.createRouter("x"), storage, logger, scheduler, dataDirectory, null));
        }
    }

    @Nested
    @DisplayName("能力取得")
    class CapabilityAccess {
        @Test
        @DisplayName("spec() 回傳傳入的 spec")
        void specReturnsPassedSpec() {
            assertSame(spec, context.spec());
        }

        @Test
        @DisplayName("router() 回傳非 null AppRouter")
        void routerReturnsNonNull() {
            AppRouter r = context.router();
            assertNotNull(r);
        }

        @Test
        @DisplayName("router() 多次呼叫回傳同一實例")
        void routerReturnsSameInstance() {
            assertSame(context.router(), context.router());
        }

        @Test
        @DisplayName("storage() 回傳非 null AppStorage")
        void storageReturnsNonNull() {
            AppStorage s = context.storage();
            assertNotNull(s);
            assertInstanceOf(AppStorageImpl.class, s);
        }

        @Test
        @DisplayName("storage() 多次呼叫回傳同一實例")
        void storageReturnsSameInstance() {
            assertSame(context.storage(), context.storage());
        }

        @Test
        @DisplayName("logger() 回傳非 null AppLogger")
        void loggerReturnsNonNull() {
            AppLogger l = context.logger();
            assertNotNull(l);
            assertInstanceOf(AppLoggerImpl.class, l);
        }

        @Test
        @DisplayName("logger() 多次呼叫回傳同一實例")
        void loggerReturnsSameInstance() {
            assertSame(context.logger(), context.logger());
        }

        @Test
        @DisplayName("scheduler() 回傳非 null AppScheduler")
        void schedulerReturnsNonNull() {
            AppScheduler s = context.scheduler();
            assertNotNull(s);
            assertInstanceOf(AppSchedulerImpl.class, s);
        }

        @Test
        @DisplayName("scheduler() 多次呼叫回傳同一實例")
        void schedulerReturnsSameInstance() {
            assertSame(context.scheduler(), context.scheduler());
        }

        @Test
        @DisplayName("getServer() 在測試環境回 null（Bukkit.getServer() = null）")
        void getServerReturnsNullInTestEnv() {
            assertNull(context.getServer());
        }

        @Test
        @DisplayName("dataDirectory() 回傳傳入的路徑")
        void dataDirectoryReturnsPassedPath() {
            assertSame(dataDirectory, context.dataDirectory());
        }

        @Test
        @DisplayName("config() 回傳傳入的 config")
        void configReturnsPassedConfig() {
            assertSame(config, context.config());
        }
    }

    @Nested
    @DisplayName("close()")
    class Close {
        @Test
        @DisplayName("implements AutoCloseable")
        void implementsAutoCloseable() {
            assertInstanceOf(AutoCloseable.class, context);
        }

        @Test
        @DisplayName("close() 不拋例外")
        void closeDoesNotThrow() {
            assertDoesNotThrow(() -> context.close());
        }

        @Test
        @DisplayName("close() 冪等")
        void closeIdempotent() {
            context.close();
            assertDoesNotThrow(() -> context.close());
        }

        @Test
        @DisplayName("close() 後 storage 已關閉（connection() 拋 IllegalStateException）")
        void closeClosesStorage() throws Exception {
            context.close();
            assertThrows(IllegalStateException.class, () -> context.storage().connection());
        }

        @Test
        @DisplayName("close() 後 logger 已關閉（不接受新寫入）")
        void closeClosesLogger() {
            context.close();
            // close 後 info 不拋，但 recentLines 回空（因為沒寫過任何日誌）
            context.logger().info("after-close");
            assertTrue(context.logger().recentLines(10).isEmpty());
        }

        @Test
        @DisplayName("close() 後 scheduler 已關閉（runAsync 拋 RejectedExecutionException）")
        void closeClosesScheduler() {
            context.close();
            assertThrows(java.util.concurrent.RejectedExecutionException.class,
                    () -> context.scheduler().runAsync(() -> {}));
        }
    }
}
