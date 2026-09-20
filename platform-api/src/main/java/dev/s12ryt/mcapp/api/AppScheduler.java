package dev.s12ryt.mcapp.api;

/**
 * App 定時任務排程器。
 *
 * <p>底層為 App 專屬 ScheduledExecutorService；提供跳回 Bukkit 主線程執行的能力。
 * App 停止時，平台取消所有排程任務並關閉執行緒池。
 */
public interface AppScheduler {

    /** 固定延遲執行：任務結束後延遲 delay 毫秒再次執行。回傳任務 id（可用於取消）。 */
    long scheduleAtFixedDelay(long initialDelayMillis, long delayMillis, Runnable task);

    /** 延遲一次性執行。回傳任務 id。 */
    long scheduleOnce(long delayMillis, Runnable task);

    /** 取消任務。任務不存在或已完成時為 no-op。 */
    void cancel(long taskId);

    /** 在 Bukkit 主線程執行（無 Bukkit 環境時直接同步執行）。 */
    void runOnMainThread(Runnable task);

    /** 立即非同步執行。 */
    void runAsync(Runnable task);
}
