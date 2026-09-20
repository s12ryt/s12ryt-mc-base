package dev.s12ryt.mcapp.core.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.s12ryt.mcapp.api.AppStorage;

/**
 * App 獨立 SQLite 存儲實作。
 *
 * <p>每個 App 擁有獨立的 SQLite 檔案（apps/{appId}/data.db）。
 * 每次 {@link #connection()} 取得新連線；App 自行管理連線生命週期（try-with-resources）。
 * 平台在 App 停止時呼叫 {@link #close()} 關閉存儲入口，之後 {@link #connection()} 拋出
 * {@link IllegalStateException}，防止已停止的 App 繼續存取資料庫。
 *
 * <p>本類別不快取連線、不共享 driver（SQLite JDBC 已內建 driver 自動註冊）。
 * 線程安全：{@link #connection()} 與 {@link #close()} 可併發呼叫；close 後所有 connection() 保證失敗。
 */
public final class AppStorageImpl implements AppStorage, AutoCloseable {

    private static final String JDBC_PREFIX = "jdbc:sqlite:";

    private final Path dbFile;
    private final String appId;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 建構存儲入口。
     *
     * @param appsDirectory 平台 apps 根目錄（如 ./apps）
     * @param appId         App 識別碼（用於建子目錄與 db 檔名）
     */
    public AppStorageImpl(Path appsDirectory, String appId) {
        java.util.Objects.requireNonNull(appsDirectory, "appsDirectory");
        this.appId = java.util.Objects.requireNonNull(appId, "appId");
        this.dbFile = appsDirectory.resolve(appId).resolve("data.db");
        try {
            Files.createDirectories(dbFile.getParent());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("無法建立存儲目錄: " + dbFile.getParent(), e);
        }
    }

    @Override
    public Connection connection() throws SQLException {
        if (closed.get()) {
            throw new IllegalStateException("App storage 已關閉: " + appId);
        }
        return DriverManager.getConnection(JDBC_PREFIX + dbFile.toAbsolutePath());
    }

    /**
     * 關閉存儲入口（不關閉已發出的連線，App 自行管理）。
     *
     * <p>冪等：多次呼叫不拋例外。
     */
    @Override
    public void close() {
        closed.set(true);
    }
}
