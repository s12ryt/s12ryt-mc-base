package dev.s12ryt.mcapp.neoforge;

import java.nio.file.Path;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import dev.s12ryt.mcapp.core.bootstrap.PlatformBootstrap;
import dev.s12ryt.mcapp.core.web.WebServer;

/**
 * NeoForge Dedicated Server mod 入口。
 *
 * <p>生命週期：
 * <ul>
 *   <li>建構子（mod 載入）— 註冊 NeoForge.EVENT_BUS 事件監聽</li>
 *   <li>ServerStartingEvent — 啟動平台（PlatformBootstrap.startup + WebServer）</li>
 *   <li>ServerStoppingEvent — 關閉 WebServer + PlatformBootstrap.shutdown</li>
 * </ul>
 *
 * <p>資料目錄：config/s12ryt-mc-base/（FMLPaths.CONFIGDIR）。
 * 不註冊任何遊戲內指令（零指令保證）。
 */
@Mod(S12rytNeoForgeMod.MOD_ID)
public final class S12rytNeoForgeMod {

    public static final String MOD_ID = "s12ryt-mc-base";

    private PlatformBootstrap bootstrap;
    private WebServer webServer;

    public S12rytNeoForgeMod() {
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        // 建立並啟動平台（注入 NeoForgeServerAdapter：MinecraftServer.execute 跳主線程）
        Path dataDirectory = FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
        bootstrap = new PlatformBootstrap(dataDirectory,
                () -> new NeoForgeServerAdapter(server));
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

        // 啟動 Web 控制台（預設埠 8080；NeoForge 端暫用固定埠，之後可加配置檔）
        int port = 8080;
        webServer = new WebServer(bootstrap);
        try {
            webServer.start(port);
            System.out.println("[s12ryt-mc-base] Web 控制台已啟動: http://localhost:" + port + "/console/");
        } catch (Exception e) {
            System.err.println("[s12ryt-mc-base] Web 控制台啟動失敗: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[s12ryt-mc-base] s12ryt MC 平台已啟動（NeoForge）");
    }

    private void onServerStopping(ServerStoppingEvent event) {
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
