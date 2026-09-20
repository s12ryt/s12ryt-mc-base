package dev.s12ryt.mcapp.forge;

import java.util.Objects;

import net.minecraft.server.MinecraftServer;

import dev.s12ryt.mcapp.core.spi.ServerAdapter;

/**
 * Forge 端的 ServerAdapter 實作。
 *
 * <p>透過 {@link MinecraftServer#execute(Runnable)} 將任務跳回伺服器主線程執行
 * （execute 內建 isSameThread 判斷：已在主線程則直接執行，否則排入下一 tick）。
 */
public final class ForgeServerAdapter implements ServerAdapter {

    private final MinecraftServer server;

    public ForgeServerAdapter(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public void runOnMainThread(Runnable task) {
        Objects.requireNonNull(task, "task");
        server.execute(task);
    }
}
