package dev.s12ryt.mcapp.api;

import java.util.List;

/**
 * App 獨立日誌流。
 *
 * <p>寫入即時持久化（檔案 + 記憶體 ring buffer），Web 控制台輪詢讀取。
 */
public interface AppLogger {

    /** INFO 級別日誌。 */
    void info(String message);

    /** WARN 級別日誌。 */
    void warn(String message);

    /** ERROR 級別日誌（可附帶例外）。 */
    void error(String message, Throwable t);

    /**
     * 讀取最近的日誌。
     *
     * @param maxLines 最多回傳行數（&lt;=0 視為預設 200 行）
     * @return 由舊到新的日誌行
     */
    List<String> recentLines(int maxLines);
}
