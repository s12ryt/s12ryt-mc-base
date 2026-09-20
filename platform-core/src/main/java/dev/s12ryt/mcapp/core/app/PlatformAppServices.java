package dev.s12ryt.mcapp.core.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import dev.s12ryt.mcapp.api.AppConfig;
import dev.s12ryt.mcapp.api.AppContext;
import dev.s12ryt.mcapp.api.AppLogger;
import dev.s12ryt.mcapp.api.AppRouter;
import dev.s12ryt.mcapp.api.AppScheduler;
import dev.s12ryt.mcapp.api.AppSpec;
import dev.s12ryt.mcapp.api.AppStorage;
import dev.s12ryt.mcapp.core.config.AppConfigImpl;
import dev.s12ryt.mcapp.core.logger.AppLoggerImpl;
import dev.s12ryt.mcapp.core.router.AppRouterRegistry;
import dev.s12ryt.mcapp.core.scheduler.AppSchedulerImpl;
import dev.s12ryt.mcapp.core.spi.ServerAdapter;
import dev.s12ryt.mcapp.core.storage.AppStorageImpl;

/**
 * AppServices 的平台實作。
 *
 * <p>為每個 App 組裝完整的 {@link AppContextImpl}，注入所有平台能力：
 * <ul>
 *   <li>{@link AppRouter} — 從 {@link AppRouterRegistry} 取得，掛載 /apps/{appId}/... 路由</li>
 *   <li>{@link AppStorage} — {@link AppStorageImpl}，獨立 SQLite（apps/{appId}/data.db）</li>
 *   <li>{@link AppLogger} — {@link AppLoggerImpl}，ring buffer + 檔案（apps/{appId}.log）</li>
 *   <li>{@link AppScheduler} — {@link AppSchedulerImpl}，守護線程池排程器</li>
 *   <li>{@link AppConfig} — {@link AppConfigImpl}，從 apps/{appId}/config.properties 載入</li>
 * </ul>
 *
 * <p>destroyContext 時關閉 context（storage、logger、scheduler）並清除路由。
 * 平台以 ConcurrentHashMap 追蹤每個 appId 的 context，確保 destroyContext 可存取。
 * createContext/destroyContext 線程安全。
 */
public final class PlatformAppServices implements AppServices {

    private final Path appsDirectory;
    private final Path logDir;
    private final AppRouterRegistry routerRegistry;
    private final Supplier<ServerAdapter> serverAdapterSupplier;
    private final Map<String, AppContextImpl> contexts = new ConcurrentHashMap<>();

    /**
     * 建構子（無伺服器注入；測試用）。
     *
     * @param appsDirectory 平台 apps 根目錄（如 ./apps）
     * @param logDir        日誌目錄（如 ./logs）
     * @param routerRegistry 路由註冊表
     */
    public PlatformAppServices(Path appsDirectory, Path logDir, AppRouterRegistry routerRegistry) {
        this(appsDirectory, logDir, routerRegistry, () -> null);
    }

    /**
     * 建構子（注入 ServerAdapter supplier，用於 AppSchedulerImpl 主線程跳回）。
     *
     * @param appsDirectory         平台 apps 根目錄（如 ./apps）
     * @param logDir                日誌目錄（如 ./logs）
     * @param routerRegistry        路由註冊表
     * @param serverAdapterSupplier  ServerAdapter 供應器（測試環境可回 null）
     */
    public PlatformAppServices(Path appsDirectory, Path logDir, AppRouterRegistry routerRegistry,
                                Supplier<ServerAdapter> serverAdapterSupplier) {
        this.appsDirectory = Objects.requireNonNull(appsDirectory, "appsDirectory");
        this.logDir = Objects.requireNonNull(logDir, "logDir");
        this.routerRegistry = Objects.requireNonNull(routerRegistry, "routerRegistry");
        this.serverAdapterSupplier = Objects.requireNonNull(serverAdapterSupplier, "serverAdapterSupplier");
    }

    @Override
    public AppContext createContext(AppSpec spec, ClassLoader appClassLoader) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(appClassLoader, "appClassLoader");

        String appId = spec.id();

        // 1. Router — 從 registry 取得（computeIfAbsent 建 list）
        AppRouter router = routerRegistry.createRouter(appId);

        // 2. Storage — 獨立 SQLite
        AppStorageImpl storage = new AppStorageImpl(appsDirectory, appId);

        // 3. Logger — ring buffer + 檔案
        AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);

        // 4. Scheduler — 守護線程池（注入 ServerAdapter supplier）
        AppSchedulerImpl scheduler = new AppSchedulerImpl(appId, serverAdapterSupplier);

        // 5. dataDirectory — apps/{appId}/data/
        Path dataDirectory = appsDirectory.resolve(appId).resolve("data");
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            // 關閉已建立的資源
            closeQuietly(storage);
            closeQuietly(logger);
            closeQuietly(scheduler);
            routerRegistry.clear(appId);
            throw new IllegalStateException("無法建立資料目錄: " + dataDirectory, e);
        }

        // 6. Config — apps/{appId}/config.properties（不存在回空 config）
        Path configFile = appsDirectory.resolve(appId).resolve("config.properties");
        AppConfig config = AppConfigImpl.load(configFile);

        // 7. 組裝 context
        AppContextImpl context = new AppContextImpl(spec, router, storage, logger, scheduler, dataDirectory, config);
        contexts.put(appId, context);
        return context;
    }

    @Override
    public void destroyContext(AppSpec spec) {
        Objects.requireNonNull(spec, "spec");
        String appId = spec.id();
        AppContextImpl context = contexts.remove(appId);
        if (context != null) {
            // 關閉 context（storage、logger、scheduler）
            closeQuietly(context);
        }
        // 清除路由（不論 context 是否存在都清，確保路由不殘留）
        routerRegistry.clear(appId);
    }

    /**
     * 取得指定 App 的 logger（供 Web 控制台查詢日誌）。
     *
     * @param appId App id
     * @return AppLogger 實例；若 App 未載入或已卸載回 null
     */
    public AppLogger getLogger(String appId) {
        AppContextImpl context = contexts.get(appId);
        return context != null ? context.logger() : null;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            System.err.println("[PlatformAppServices] close failed: " + e.getMessage());
        }
    }
}
