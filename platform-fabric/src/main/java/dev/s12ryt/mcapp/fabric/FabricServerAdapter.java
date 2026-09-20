package dev.s12ryt.mcapp.fabric;

import java.util.Objects;

import net.minecraft.server.MinecraftServer;

import dev.s12ryt.mcapp.core.spi.ServerAdapter;

/**
 * Fabric 端的 {@link ServerAdapter} 實作。
 *
 * <p>使用 {@link MinecraftServer#execute(Runnable)} 將任務跳回伺服器主線程執行。
 * MinecraftServer.execute 內建 isOnThread 判斷：若已在主線程則直接執行，
 * 否則排入下一 tick 執行。
 */
public final class FabricServerAdapter implements ServerAdapter {

    private final MinecraftServer server;

    public FabricServerAdapter(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public void runOnMainThread(Runnable task) {
        Objects.requireNonNull(task, "task");
        server.execute(task);
    }
}
