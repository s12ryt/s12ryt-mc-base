package dev.s12ryt.mcapp.core.spi;

import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

/**
 * Bukkit/Paper 伺服器適配器：透過 BukkitScheduler 將任務跳回主線程。
 *
 * <p>當 Bukkit 伺服器存在時使用 {@code server.getScheduler().runTask(plugin, task)}；
 * plugin 供應器回傳 null 時（測試環境 fallback）退回同步執行；
 * 無伺服器時直接同步執行。
 */
public final class BukkitServerAdapter implements ServerAdapter {

    private final Supplier<Plugin> pluginSupplier;

    /**
     * @param pluginSupplier plugin 實例供應器（延遲取用；僅在有伺服器時呼叫）
     */
    public BukkitServerAdapter(Supplier<Plugin> pluginSupplier) {
        this.pluginSupplier = Objects.requireNonNull(pluginSupplier, "pluginSupplier");
    }

    @Override
    public void runOnMainThread(Runnable task) {
        Objects.requireNonNull(task, "task");
        Server server = Bukkit.getServer();
        if (server == null) {
            // 無伺服器環境（單元測試）：直接同步執行
            task.run();
            return;
        }
        org.bukkit.scheduler.BukkitScheduler scheduler = server.getScheduler();
        Plugin plugin = pluginSupplier.get();
        if (plugin != null) {
            scheduler.runTask(plugin, task);
        } else {
            // plugin 未就緒的 fallback（測試環境）
            task.run();
        }
    }
}
