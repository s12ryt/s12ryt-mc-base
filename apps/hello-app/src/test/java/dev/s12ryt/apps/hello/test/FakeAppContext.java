package dev.s12ryt.apps.hello.test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import dev.s12ryt.mcapp.api.AppConfig;
import dev.s12ryt.mcapp.api.AppContext;
import dev.s12ryt.mcapp.api.AppLogger;
import dev.s12ryt.mcapp.api.AppRouter;
import dev.s12ryt.mcapp.api.AppScheduler;
import dev.s12ryt.mcapp.api.AppSpec;
import dev.s12ryt.mcapp.api.AppStorage;

/**
 * 測試用 AppContext：可控的 stub。
 */
public class FakeAppContext implements AppContext {

    private final AppSpec spec;
    private final FakeRouter router;
    private final AppStorage storage;
    private final FakeLogger logger;
    private final FakeScheduler scheduler;
    private final Path dataDirectory;
    private final AppConfig config;

    public FakeAppContext(AppSpec spec, FakeRouter router, AppStorage storage,
                          FakeLogger logger, FakeScheduler scheduler,
                          Path dataDirectory, AppConfig config) {
        this.spec = spec;
        this.router = router;
        this.storage = storage;
        this.logger = logger;
        this.scheduler = scheduler;
        this.dataDirectory = dataDirectory;
        this.config = config;
    }

    @Override public AppSpec spec() { return spec; }
    @Override public AppRouter router() { return router; }
    @Override public AppStorage storage() { return storage; }
    @Override public AppLogger logger() { return logger; }
    @Override public AppScheduler scheduler() { return scheduler; }
    @Override public org.bukkit.Server getServer() { return null; }
    @Override public Path dataDirectory() { return dataDirectory; }
    @Override public AppConfig config() { return config; }

    public FakeRouter fakeRouter() { return router; }
    public FakeLogger fakeLogger() { return logger; }
    public FakeScheduler fakeScheduler() { return scheduler; }

    // ─── FakeLogger ───

    public static class FakeLogger implements AppLogger {
        private final List<String> lines = Collections.synchronizedList(new ArrayList<>());

        @Override public void info(String message) { lines.add("INFO " + message); }
        @Override public void warn(String message) { lines.add("WARN " + message); }
        @Override public void error(String message, Throwable throwable) { lines.add("ERROR " + message); }
        @Override public java.util.List<String> recentLines(int maxLines) {
            int size = lines.size();
            int from = Math.max(0, size - maxLines);
            return List.copyOf(lines.subList(from, size));
        }

        public List<String> lines() { return List.copyOf(lines); }
    }

    // ─── FakeScheduler ───

    public static class FakeScheduler implements AppScheduler {
        public final List<Runnable> asyncTasks = new ArrayList<>();
        public final List<Runnable> mainThreadTasks = new ArrayList<>();
        public final List<Runnable> scheduledTasks = new ArrayList<>();

        @Override public long scheduleAtFixedDelay(long initialDelay, long delay, Runnable task) {
            scheduledTasks.add(task);
            return 1L;
        }
        @Override public long scheduleOnce(long delay, Runnable task) {
            scheduledTasks.add(task);
            return 1L;
        }
        @Override public void cancel(long taskId) {}
        @Override public void runOnMainThread(Runnable task) { mainThreadTasks.add(task); }
        @Override public void runAsync(Runnable task) { asyncTasks.add(task); }
    }

    // ─── InMemoryStorage ───

    public static class InMemoryStorage implements AppStorage {
        private Connection sharedConn;
        private boolean closed = false;

        public InMemoryStorage() {
            try {
                Class.forName("org.sqlite.JDBC");
                sharedConn = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
                sharedConn.setAutoCommit(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Connection connection() throws SQLException {
            if (closed) throw new IllegalStateException("storage closed");
            // 用 Proxy 包裝，使 close() 為 no-op
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("close") && method.getParameterCount() == 0) {
                            return null; // no-op
                        }
                        return method.invoke(sharedConn, args);
                    });
        }

        public void close() {
            closed = true;
            try { sharedConn.close(); } catch (SQLException e) { /* ignore */ }
        }
    }
}
