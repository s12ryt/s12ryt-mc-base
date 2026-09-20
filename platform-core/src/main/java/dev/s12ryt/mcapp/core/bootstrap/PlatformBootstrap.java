package dev.s12ryt.mcapp.core.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.plugin.Plugin;

import dev.s12ryt.mcapp.core.app.AppContainer;
import dev.s12ryt.mcapp.core.app.PlatformAppServices;
import dev.s12ryt.mcapp.core.auth.AuthManager;
import dev.s12ryt.mcapp.core.router.AppRouterRegistry;

/**
 * 平台生命週期管理器。
 *
 * <p>不依賴 JavaPlugin，可獨立單元測試。負責：
 * <ul>
 *   <li>建立資料目錄結構（apps/、logs/、data/）</li>
 *   <li>初始化 {@link AuthManager}（首次生成密碼，之後載入既有）</li>
 *   <li>初始化 {@link AppRouterRegistry}、{@link PlatformAppServices}、{@link AppContainer}</li>
 *   <li>startup 時 loadAll 載入所有 App jar</li>
 *   <li>shutdown 時 unload 所有 App + 清除路由</li>
 * </ul>
 *
 * <p>S12rytPlugin（Paper plugin 主類別）是薄封裝層，呼叫本類別。
 * 本類別不提供任何指令註冊 API（零遊戲指令保證）。
 */
public final class PlatformBootstrap {

    private final Path dataDirectory;
    private final Path appsDirectory;
    private final Path logsDirectory;
    private final Supplier<Plugin> pluginSupplier;

    private AuthManager authManager;
    private AppRouterRegistry routerRegistry;
    private PlatformAppServices appServices;
    private AppContainer appContainer;
    private volatile boolean started = false;

    /**
     * 建構子（無 plugin 注入；測試用）。
     *
     * @param dataDirectory 平台根資料目錄（apps/、logs/、data/ 建立於此之下）
     */
    public PlatformBootstrap(Path dataDirectory) {
        this(dataDirectory, () -> null);
    }

    /**
     * 建構子（注入 plugin supplier）。
     *
     * @param dataDirectory  平台根資料目錄
     * @param pluginSupplier  plugin 實例供應器（用於 AppSchedulerImpl 主線程跳回）
     */
    public PlatformBootstrap(Path dataDirectory, Supplier<Plugin> pluginSupplier) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.pluginSupplier = Objects.requireNonNull(pluginSupplier, "pluginSupplier");
        this.appsDirectory = dataDirectory.resolve("apps");
        this.logsDirectory = dataDirectory.resolve("logs");
    }

    /**
     * 啟動平台：建立目錄、初始化元件、載入 App。
     *
     * <p>可重複呼叫（冪等）；第二次呼叫為 no-op。
     *
     * @throws IllegalStateException 若目錄建立或認證初始化失敗
     */
    public synchronized void startup() {
        if (started) {
            return;
        }

        // 建立目錄結構
        try {
            Files.createDirectories(appsDirectory);
            Files.createDirectories(logsDirectory);
            Files.createDirectories(dataDirectory.resolve("data"));
        } catch (IOException e) {
            throw new IllegalStateException("無法建立資料目錄: " + e.getMessage(), e);
        }

        // 初始化認證
        try {
            authManager = AuthManager.init(dataDirectory.resolve("data").resolve("auth.json"));
        } catch (IOException e) {
            throw new IllegalStateException("認證初始化失敗: " + e.getMessage(), e);
        }

        // 初始化路由註冊表
        routerRegistry = new AppRouterRegistry();

        // 初始化 App 服務
        appServices = new PlatformAppServices(appsDirectory, logsDirectory, routerRegistry, pluginSupplier);

        // 初始化 App 容器
        appContainer = new AppContainer(appsDirectory, appServices, getClass().getClassLoader());

        // 載入所有 App
        appContainer.loadAll();

        started = true;
    }

    /**
     * 關閉平台：unload 所有 App、清除路由。
     *
     * <p>冪等；可重複呼叫。
     */
    public synchronized void shutdown() {
        if (!started) {
            return;
        }

        // 關閉 App 容器（unload 所有 App）
        if (appContainer != null) {
            appContainer.close();
        }

        // 清除所有路由
        if (routerRegistry != null) {
            routerRegistry.clearAll();
        }

        started = false;
    }

    /** 取得 AuthManager（startup 前為 null）。 */
    public AuthManager getAuthManager() {
        return authManager;
    }

    /** 取得 AppRouterRegistry（startup 前為 null）。 */
    public AppRouterRegistry getRouterRegistry() {
        return routerRegistry;
    }

    /** 取得 AppContainer（startup 前為 null）。 */
    public AppContainer getAppContainer() {
        return appContainer;
    }

    /** 取得 PlatformAppServices（startup 前為 null）。 */
    public PlatformAppServices getAppServices() {
        return appServices;
    }

    /** 是否已啟動。 */
    public boolean isStarted() {
        return started;
    }
}
