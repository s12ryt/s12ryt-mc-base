package dev.s12ryt.mcapp.core.app.testapps;

import dev.s12ryt.mcapp.api.App;
import dev.s12ryt.mcapp.api.AppContext;

import java.nio.file.Files;

/**
 * 測試用 App：記錄啟用/停用與實例編號。
 *
 * <p>實例編號來自 classloader 級 static，用於驗證隔離與重載（每次重載應從 1 重新計數）。
 * 故意放在獨立 source set（不進 test classpath），確保由 App 專屬 ClassLoader 載入。
 */
public class RecordingApp implements App {

    public static int instances = 0;

    private AppContext ctx;
    private final int instance;

    public RecordingApp() {
        instance = ++instances;
    }

    @Override
    public void onEnable(AppContext context) throws Exception {
        this.ctx = context;
        Files.writeString(context.dataDirectory().resolve("started.txt"), context.spec().id());
        context.logger().info("enabled#" + instance
                + " server=" + (context.getServer() == null ? "null" : "present"));
    }

    @Override
    public void onDisable() {
        ctx.logger().info("disabled#" + instance);
    }
}
