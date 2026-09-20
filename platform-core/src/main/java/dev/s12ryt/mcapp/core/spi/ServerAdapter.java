package dev.s12ryt.mcapp.core.spi;

/**
 * 伺服器適配器 SPI：隔離平台對特定伺服器加載器（Paper/Fabric/NeoForge/Forge）的依賴。
 *
 * <p>平台核心（platform-core）不直接依賴任何加載器 API；各加載器入口
 * （S12rytPlugin / Fabric 入口 / NeoForge @Mod / Forge @Mod）注入各自的
 * {@link ServerAdapter} 實作。
 */
public interface ServerAdapter {

    /**
     * 在伺服器主線程上執行任務。
     *
     * <p>行為依實作而定：
     * <ul>
     *   <li>Bukkit/Paper：BukkitScheduler.runTask</li>
     *   <li>Mod 端：server.execute（無伺服器時同步執行）</li>
     *   <li>預設（無伺服器環境）：直接在當前線程同步執行</li>
     * </ul>
     *
     * @param task 要執行的任務（不可為 null）
     */
    void runOnMainThread(Runnable task);
}
