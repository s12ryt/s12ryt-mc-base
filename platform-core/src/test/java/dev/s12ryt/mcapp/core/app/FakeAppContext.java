package dev.s12ryt.mcapp.core.app;

import dev.s12ryt.mcapp.api.AppConfig;
import dev.s12ryt.mcapp.api.AppContext;
import dev.s12ryt.mcapp.api.AppLogger;
import dev.s12ryt.mcapp.api.AppRouter;
import dev.s12ryt.mcapp.api.AppScheduler;
import dev.s12ryt.mcapp.api.AppSpec;
import dev.s12ryt.mcapp.api.AppStorage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Server;

/**
 * 測試用 AppContext 替身。
 *
 * <p>logger 將所有日誌行（含級別前綴）寫入同步 List，供測試斷言。
 * scheduler 的 runAsync / runOnMainThread 直接同步執行（無獨立執行緒）。
 * getServer 回傳 Bukkit.getServer()（測試環境為 null）。
 */
public class FakeAppContext implements AppContext {

    private final AppSpec spec;
    private final Path dataDirectory;
    private final List<String> logLines;

    public FakeAppContext(AppSpec spec, Path dataDirectory) {
        this.spec = spec;
        this.dataDirectory = dataDirectory;
        this.logLines = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    public AppSpec spec() {
        return spec;
    }

    @Override
    public AppRouter router() {
        return new AppRouter() {
            @Override
            public void get(String pattern, AppHandler handler) { }
            @Override
            public void post(String pattern, AppHandler handler) { }
            @Override
            public void put(String pattern, AppHandler handler) { }
            @Override
            public void delete(String pattern, AppHandler handler) { }
        };
    }

    @Override
    public AppStorage storage() {
        throw new UnsupportedOperationException("FakeAppContext.storage not supported");
    }

    @Override
    public AppLogger logger() {
        return new AppLogger() {
            @Override
            public void info(String message) {
                logLines.add("INFO " + message);
            }
            @Override
            public void warn(String message) {
                logLines.add("WARN " + message);
            }
            @Override
            public void error(String message, Throwable t) {
                logLines.add("ERROR " + message);
            }
            @Override
            public List<String> recentLines(int maxLines) {
                int cap = maxLines <= 0 ? 200 : maxLines;
                int size = logLines.size();
                int from = Math.max(0, size - cap);
                return List.copyOf(logLines.subList(from, size));
            }
        };
    }

    @Override
    public AppScheduler scheduler() {
        return new AppScheduler() {
            @Override
            public long scheduleAtFixedDelay(long initialDelayMillis, long delayMillis, Runnable task) {
                return 0L;
            }
            @Override
            public long scheduleOnce(long delayMillis, Runnable task) {
                return 0L;
            }
            @Override
            public void cancel(long taskId) { }
            @Override
            public void runOnMainThread(Runnable task) {
                task.run();
            }
            @Override
            public void runAsync(Runnable task) {
                task.run();
            }
        };
    }

    @Override
    public Server getServer() {
        return org.bukkit.Bukkit.getServer();
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public AppConfig config() {
        return new AppConfig() {
            @Override
            public String getString(String key) { return null; }
            @Override
            public String getString(String key, String def) { return def; }
            @Override
            public int getInt(String key, int def) { return def; }
            @Override
            public boolean getBoolean(String key, boolean def) { return def; }
            @Override
            public boolean containsKey(String key) { return false; }
        };
    }

    /** 取得此 context 收集的日誌行（供測試斷言）。 */
    public List<String> logLines() {
        return List.copyOf(logLines);
    }
}
