package dev.s12ryt.mcapp.core.app;

import java.nio.file.Path;
import java.util.Objects;

import org.bukkit.Bukkit;

import dev.s12ryt.mcapp.api.AppConfig;
import dev.s12ryt.mcapp.api.AppContext;
import dev.s12ryt.mcapp.api.AppLogger;
import dev.s12ryt.mcapp.api.AppRouter;
import dev.s12ryt.mcapp.api.AppScheduler;
import dev.s12ryt.mcapp.api.AppSpec;
import dev.s12ryt.mcapp.api.AppStorage;

/**
 * AppContext 實作：組裝所有平台能力（router、storage、logger、scheduler、server、dataDirectory、config）。
 *
 * <p>由 {@link PlatformAppServices#createContext} 建構，注入到每個 App 的 onEnable。
 * <p>close() 時關閉 storage、logger、scheduler（router 由 registry 統一清理，不由這裡關閉）。
 */
public final class AppContextImpl implements AppContext, AutoCloseable {

    private final AppSpec spec;
    private final AppRouter router;
    private final AppStorage storage;
    private final AppLogger logger;
    private final AppScheduler scheduler;
    private final Path dataDirectory;
    private final AppConfig config;

    /**
     * 建構子。
     *
     * @param spec           App 元資料
     * @param router         App 專屬路由器（從 AppRouterRegistry 取得）
     * @param storage        App 專屬 SQLite 存儲
     * @param logger         App 專屬日誌器
     * @param scheduler      App 專屬排程器
     * @param dataDirectory  App 私有資料目錄
     * @param config         App 配置
     */
    public AppContextImpl(AppSpec spec, AppRouter router, AppStorage storage, AppLogger logger,
                          AppScheduler scheduler, Path dataDirectory, AppConfig config) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.router = Objects.requireNonNull(router, "router");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public AppSpec spec() {
        return spec;
    }

    @Override
    public AppRouter router() {
        return router;
    }

    @Override
    public AppStorage storage() {
        return storage;
    }

    @Override
    public AppLogger logger() {
        return logger;
    }

    @Override
    public AppScheduler scheduler() {
        return scheduler;
    }

    @Override
    public Object getServer() {
        return Bukkit.getServer();
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public AppConfig config() {
        return config;
    }

    /**
     * 關閉 context：關閉 storage、logger、scheduler。
     *
     * <p>router 由 AppRouterRegistry 統一清理（clear(appId)），不由這裡關閉。
     * <p>冪等：多次呼叫不拋例外。
     */
    @Override
    public void close() {
        closeQuietly(storage);
        closeQuietly(logger);
        closeQuietly(scheduler);
    }

    private static void closeQuietly(Object obj) {
        if (obj instanceof AutoCloseable ac) {
            try {
                ac.close();
            } catch (Exception e) {
                // 靜默關閉（不中斷其他資源的釋放）
                System.err.println("[AppContextImpl] close failed for " + obj.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }
}
