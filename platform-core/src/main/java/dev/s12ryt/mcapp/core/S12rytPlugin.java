package dev.s12ryt.mcapp.core;

import org.bukkit.plugin.java.JavaPlugin;

import dev.s12ryt.mcapp.core.bootstrap.PlatformBootstrap;
import dev.s12ryt.mcapp.core.spi.BukkitServerAdapter;
import dev.s12ryt.mcapp.core.web.WebServer;

/**
 * Paper 1.21.4 插件主類別。
 *
 * <p>本類別是薄封裝層，將生命週期管理委託給 {@link PlatformBootstrap}。
 * 不註冊任何遊戲內指令（零指令保證）。
 *
 * <p>plugin.yml 不含 commands 區段。
 */
public final class S12rytPlugin extends JavaPlugin {

    private PlatformBootstrap bootstrap;
    private WebServer webServer;

    @Override
    public void onEnable() {
        // 提取預設 config.yml（若磁碟上無 config.yml），
        // 讓管理員可修改 web.port 等配置
        saveDefaultConfig();

        // 建立並啟動平台（注入 BukkitServerAdapter：scheduler 主線程跳回用）
        bootstrap = new PlatformBootstrap(getDataFolder().toPath(),
                () -> new BukkitServerAdapter(() -> this));
        try {
            bootstrap.startup();
        } catch (IllegalStateException e) {
            getLogger().severe("平台啟動失敗: " + e.getMessage());
            e.printStackTrace();
            // 不禁用插件——讓管理員可透過日誌排查問題
            return;
        }

        // 輸出首次生成的密碼（僅首次）
        if (bootstrap.getAuthManager() != null) {
            String password = bootstrap.getAuthManager().currentPassword();
            if (password != null) {
                getLogger().info("========================================");
                getLogger().info("首次啟動：管理員密碼已生成");
                getLogger().info("密碼: " + password);
                getLogger().info("請妥善保存，此密碼不會再次顯示");
                getLogger().info("========================================");
            }
        }

        // 啟動 Web 控制台（預設埠 8080）
        int port = getConfig().getInt("web.port", 8080);
        webServer = new WebServer(bootstrap);
        try {
            webServer.start(port);
            getLogger().info("Web 控制台已啟動: http://localhost:" + port + "/console/");
        } catch (Exception e) {
            getLogger().severe("Web 控制台啟動失敗: " + e.getMessage());
            e.printStackTrace();
        }

        getLogger().info("s12ryt MC 平台已啟動");
    }

    @Override
    public void onDisable() {
        if (webServer != null) {
            webServer.close();
            webServer = null;
        }
        if (bootstrap != null) {
            bootstrap.shutdown();
            bootstrap = null;
        }
        getLogger().info("s12ryt MC 平台已關閉");
    }

    /**
     * 取得平台生命週期管理器（供 Web 控制台層存取）。
     *
     * @return PlatformBootstrap 實例；若未啟動則為 null
     */
    public PlatformBootstrap getBootstrap() {
        return bootstrap;
    }
}
