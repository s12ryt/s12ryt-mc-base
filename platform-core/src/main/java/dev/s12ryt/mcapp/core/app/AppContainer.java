package dev.s12ryt.mcapp.core.app;

import dev.s12ryt.mcapp.api.App;
import dev.s12ryt.mcapp.api.AppContext;
import dev.s12ryt.mcapp.api.AppSpec;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * App 容器：管理 App 的掃描、隔離載入、啟用、卸載與重載。
 *
 * <p>每個 App 以獨立 {@link URLClassLoader} 載入（parent-first，共享 platform-api 與 Bukkit API），
 * 確保 App 類別互相隔離。卸載時關閉 ClassLoader 以釋放 jar 檔案鎖（Windows 上尤其重要）。
 *
 * <p>載入期或啟用期失敗均不拋例外：{@link #load(Path)} 永遠回傳 {@link AppHandle}，
 * 成功為 {@link AppState#RUNNING}，失敗為 {@link AppState#FAILED}（含錯誤訊息）。
 * 啟用失敗的 App 保留 FAILED 條目供 Web 控制台檢視。
 */
public final class AppContainer implements AutoCloseable {

    /** App 運行狀態。 */
    public enum AppState {
        /** 啟用成功，正在運行。 */
        RUNNING,
        /** 載入或啟用失敗。 */
        FAILED
    }

    /**
     * App 的不可變快照（供 Web 控制台展示）。
     *
     * @param id      App id
     * @param name    App 名稱
     * @param version App 版本
     * @param jar     jar 檔路徑
     * @param state   運行狀態
     * @param error   失敗時的錯誤訊息（成功時為空字串）
     */
    public record AppHandle(String id, String name, String version, Path jar, AppState state, String error) {
    }

    /** 內部條目：保留運行中的 App 實例與 ClassLoader。 */
    private record Entry(AppSpec spec, Path jar, ClassLoader loader, App app, AppHandle handle) {
    }

    /** jar 內 app.properties 的原始讀取結果（不拋例外）。 */
    private record RawMeta(String id, String name, String version, String main, String error) {
    }

    /** 內部載入失敗信號。 */
    private static final class AppLoadException extends Exception {
        AppLoadException(String message) {
            super(message);
        }
        AppLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Path appsDirectory;
    private final AppServices services;
    private final ClassLoader sharedClassLoader;
    private final Logger logger = Logger.getLogger(AppContainer.class.getName());

    private final ConcurrentHashMap<String, Entry> apps = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();

    /**
     * @param appsDirectory     存放 App jar 的目錄（不存在時 loadAll 會自動建立）
     * @param services          平台服務接縫
     * @param sharedClassLoader 共享 ClassLoader（parent；含 platform-api + Bukkit API）
     */
    public AppContainer(Path appsDirectory, AppServices services, ClassLoader sharedClassLoader) {
        this.appsDirectory = java.util.Objects.requireNonNull(appsDirectory, "appsDirectory");
        this.services = java.util.Objects.requireNonNull(services, "services");
        this.sharedClassLoader = java.util.Objects.requireNonNull(sharedClassLoader, "sharedClassLoader");
    }

    // ─── loadAll ───

    /**
     * 掃描 appsDirectory 下所有 .jar 檔（不含子目錄），逐一載入。
     *
     * @return 所有 jar 的載入結果（順序依檔名排序）
     */
    public List<AppHandle> loadAll() {
        try {
            Files.createDirectories(appsDirectory);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to create apps directory: " + appsDirectory, e);
            return List.of();
        }

        List<Path> jars = new ArrayList<>();
        try (var stream = Files.list(appsDirectory)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .sorted()
                    .forEach(jars::add);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to list apps directory: " + appsDirectory, e);
            return List.of();
        }

        List<AppHandle> handles = new ArrayList<>();
        for (Path jar : jars) {
            handles.add(load(jar));
        }
        return handles;
    }

    // ─── load ───

    /**
     * 載入單一 jar。成功回 RUNNING handle，失敗回 FAILED handle（不拋例外）。
     *
     * @param jar jar 檔路徑
     * @return 載入結果
     */
    public AppHandle load(Path jar) {
        java.util.Objects.requireNonNull(jar, "jar");

        synchronized (lifecycleLock) {
            // 讀取元資料
            RawMeta meta = readMeta(jar);
            if (meta.error() != null) {
                return new AppHandle(
                        meta.id() != null ? meta.id() : jar.getFileName().toString(),
                        meta.name() != null ? meta.name() : "",
                        meta.version() != null ? meta.version() : "",
                        jar, AppState.FAILED, meta.error());
            }

            // 驗證 id 格式
            AppSpec spec = new AppSpec(meta.id(), meta.name(), meta.version(), meta.main());
            if (!spec.isValidId()) {
                return new AppHandle(meta.id(), meta.name(), meta.version(), jar,
                        AppState.FAILED, "app.id 格式無效: " + meta.id());
            }

            // 檢查重複 id
            if (apps.containsKey(spec.id())) {
                return new AppHandle(spec.id(), spec.name(), spec.version(), jar,
                        AppState.FAILED, "app id 已載入: " + spec.id());
            }

            // 嘗試載入並啟動
            try {
                return startApp(jar, spec);
            } catch (AppLoadException e) {
                return new AppHandle(spec.id(), spec.name(), spec.version(), jar,
                        AppState.FAILED, e.getMessage());
            }
        }
    }

    private AppHandle startApp(Path jar, AppSpec spec) throws AppLoadException {
        // 建立 App 專屬 ClassLoader
        URLClassLoader loader;
        try {
            loader = new URLClassLoader(
                    "app-" + spec.id(),
                    new URL[]{jar.toUri().toURL()},
                    sharedClassLoader);
        } catch (Exception e) {
            throw new AppLoadException("無法建立 ClassLoader: " + e.getMessage(), e);
        }

        // 載入 main 類
        Class<?> mainClass;
        try {
            mainClass = Class.forName(spec.main(), true, loader);
        } catch (ClassNotFoundException e) {
            closeLoaderQuietly(loader);
            throw new AppLoadException("ClassNotFoundException: " + spec.main(), e);
        } catch (Exception e) {
            closeLoaderQuietly(loader);
            throw new AppLoadException("無法載入主類 " + spec.main() + ": " + e.getMessage(), e);
        }

        // 檢查是否實作 App
        if (!App.class.isAssignableFrom(mainClass)) {
            closeLoaderQuietly(loader);
            throw new AppLoadException("主類未實作 App 介面: " + spec.main());
        }

        // 反射建構
        App app;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends App> appClass = (Class<? extends App>) mainClass;
            app = appClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            closeLoaderQuietly(loader);
            throw new AppLoadException("無法實例化 App: " + e.getMessage(), e);
        }

        // 建立 context
        AppContext context;
        try {
            context = services.createContext(spec, loader);
        } catch (Exception e) {
            closeLoaderQuietly(loader);
            throw new AppLoadException("createContext 失敗: " + e.getMessage(), e);
        }

        // 啟用 App
        try {
            app.onEnable(context);
        } catch (Throwable e) {
            // 啟用失敗：銷毀 context + 關閉 loader，但保留 FAILED 條目
            safeDestroyContext(spec);
            closeLoaderQuietly(loader);
            AppHandle failed = new AppHandle(spec.id(), spec.name(), spec.version(), jar,
                    AppState.FAILED, e.getMessage() != null ? e.getMessage() : e.toString());
            apps.put(spec.id(), new Entry(spec, jar, null, null, failed));
            logger.log(Level.WARNING, "App " + spec.id() + " enable failed: " + e.getMessage(), e);
            return failed;
        }

        AppHandle handle = new AppHandle(spec.id(), spec.name(), spec.version(), jar,
                AppState.RUNNING, "");
        apps.put(spec.id(), new Entry(spec, jar, loader, app, handle));
        logger.info("App started: " + spec.id() + " v" + spec.version());
        return handle;
    }

    // ─── unload ───

    /**
     * 卸載 App。RUNNING → onDisable + destroyContext + close loader。
     * FAILED → 僅移除條目（不呼叫 destroyContext，避免雙重釋放）。
     *
     * @param appId App id
     * @return true 若有移除條目；false 若 id 不存在
     */
    public boolean unload(String appId) {
        synchronized (lifecycleLock) {
            Entry entry = apps.remove(appId);
            if (entry == null) {
                return false;
            }
            if (entry.app() != null) {
                // RUNNING：呼叫 onDisable（catch Throwable）+ destroyContext + close loader
                try {
                    entry.app().onDisable();
                } catch (Throwable e) {
                    logger.log(Level.WARNING, "App " + appId + " onDisable threw: " + e.getMessage(), e);
                }
                safeDestroyContext(entry.spec());
                closeLoaderQuietly(entry.loader());
            }
            // FAILED（app==null）僅移除條目
            logger.info("App unloaded: " + appId);
            return true;
        }
    }

    // ─── reload ───

    /**
     * 重載 App：unload 後以原 jar 重新 load。
     *
     * @param appId App id
     * @return 新 handle；若 id 不存在回 empty
     */
    public Optional<AppHandle> reload(String appId) {
        synchronized (lifecycleLock) {
            Entry entry = apps.get(appId);
            if (entry == null) {
                return Optional.empty();
            }
            Path jar = entry.jar();
            unload(appId);
            return Optional.of(load(jar));
        }
    }

    // ─── get / list ───

    public Optional<AppHandle> get(String appId) {
        Entry entry = apps.get(appId);
        return entry != null ? Optional.of(entry.handle()) : Optional.empty();
    }

    public List<AppHandle> list() {
        return apps.values().stream()
                .map(Entry::handle)
                .toList();
    }

    // ─── close ───

    @Override
    public void close() {
        // 防止與併發 load 競態：close 期間不允許新的載入插入
        // （unload 內部再次 synchronized(lifecycleLock) 為可重入，安全）
        synchronized (lifecycleLock) {
            List<String> ids = List.copyOf(apps.keySet());
            for (String id : ids) {
                unload(id);
            }
        }
    }

    // ─── 內部 helper ───

    /**
     * 從 jar 讀取 app.properties（不拋例外；錯誤放 RawMeta.error）。
     */
    private RawMeta readMeta(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            var entry = jf.getJarEntry("app.properties");
            if (entry == null) {
                return new RawMeta(null, null, null, null,
                        "缺少 app.properties（jar: " + jar.getFileName() + "）");
            }
            Properties props = new Properties();
            try (InputStream in = jf.getInputStream(entry)) {
                props.load(in);
            }
            String id = props.getProperty("app.id", "");
            String name = props.getProperty("app.name", "");
            String version = props.getProperty("app.version", "");
            String main = props.getProperty("app.main", "");
            if (main.isEmpty()) {
                return new RawMeta(id, name, version, null,
                        "app.main 未設定或為空");
            }
            return new RawMeta(id, name, version, main, null);
        } catch (IOException e) {
            return new RawMeta(null, null, null, null,
                    "無法讀取 jar: " + e.getMessage());
        }
    }

    private void closeLoaderQuietly(ClassLoader loader) {
        if (loader instanceof URLClassLoader ucl) {
            try {
                ucl.close();
            } catch (IOException e) {
                logger.log(Level.FINE, "ClassLoader.close failed: " + e.getMessage(), e);
            }
        }
    }

    private void safeDestroyContext(AppSpec spec) {
        try {
            services.destroyContext(spec);
        } catch (Exception e) {
            logger.log(Level.WARNING, "destroyContext failed for " + spec.id() + ": " + e.getMessage(), e);
        }
    }
}
