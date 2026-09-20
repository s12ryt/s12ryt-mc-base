package dev.s12ryt.mcapp.api;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * App 獨立 SQLite 存儲（類 Docker volume）。
 *
 * <p>每次呼叫 {@link #connection()} 都取得新的連線；App 自行管理連線生命週期（try-with-resources）。
 * 平台在 App 停止時關閉所有由本介面衍生的資源。
 */
public interface AppStorage {

    /** 取得新資料庫連線。App 停止後呼叫應拋出 IllegalStateException。 */
    Connection connection() throws SQLException;
}
