package dev.s12ryt.mcapp.fabric;

import java.nio.file.Path;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import dev.s12ryt.mcapp.core.bootstrap.PlatformBootstrap;
import dev.s12ryt.mcapp.core.web.WebServer;

/**
 * Fabric Dedicated Server mod 入口。
 *
 * <p>生命週期：
 * <ul>
 *   <li>onInitializeServer（mod 初始化）— 註冊 ServerLifecycleEvents</li>
 *   <li>SERVER_STARTING — 啟動平台（PlatformBootstrap.startup + WebServer）</li>
 *   <li>SERVER_STOPPING — 關閉 WebServer + PlatformBootstrap.shutdown</li>
 * </ul>
 *
 * <p>資料目錄：config/s12ryt-mc-base/（FabricLoader.getConfigDir()）。
 * 不註冊任何遊戲內指令（零指令保證）。
 */
public final class S12rytFabricMod implements DedicatedServerModInitializer {

    public static final String MOD_ID = "s12ryt-mc-base";

    private PlatformBootstrap bootstrap;
    private WebServer webServer;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> onServerStarting(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> onServerStopping());
    }

    private void onServerStarting(net.minecraft.server.MinecraftServer server) {
        // 建立並啟動平台（注入 FabricServerAdapter：MinecraftServer.execute 跳主線程）
        Path dataDirectory = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        bootstrap = new PlatformBootstrap(dataDirectory,
                () -> new FabricServerAdapter(server));
        try {
            bootstrap.startup();
        } catch (IllegalStateException e) {
            System.err.println("[s12ryt-mc-base] 平台啟動失敗: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // 輸出首次生成的密碼（僅首次）
        if (bootstrap.getAuthManager() != null) {
            String password = bootstrap.getAuthManager().currentPassword();
            if (password != null) {
                System.out.println("[s12ryt-mc-base] ========================================");
                System.out.println("[s12ryt-mc-base] 首次啟動：管理員密碼已生成");
                System.out.println("[s12ryt-mc-base] 密碼: " + password);
                System.out.println("[s12ryt-mc-base] 請妥善保存，此密碼不會再次顯示");
                System.out.println("[s12ryt-mc-base] ========================================");
            }
        }

        // 啟動 Web 控制台（預設埠 8080；Fabric 端暫用固定埠，之後可加配置檔）
        int port = 8080;
        webServer = new WebServer(bootstrap);
        try {
            webServer.start(port);
            System.out.println("[s12ryt-mc-base] Web 控制台已啟動: http://localhost:" + port + "/console/");
        } catch (Exception e) {
            System.err.println("[s12ryt-mc-base] Web 控制台啟動失敗: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[s12ryt-mc-base] s12ryt MC 平台已啟動（Fabric）");
    }

    private void onServerStopping() {
        if (webServer != null) {
            webServer.close();
            webServer = null;
        }
        if (bootstrap != null) {
            bootstrap.shutdown();
            bootstrap = null;
        }
        System.out.println("[s12ryt-mc-base] s12ryt MC 平台已關閉");
    }
}
