package dev.s12ryt.mcapp.core.app.testapps;

import dev.s12ryt.mcapp.api.App;
import dev.s12ryt.mcapp.api.AppContext;

/**
 * onEnable 拋出例外的 App：驗證啟用失敗的清理路徑（onDisable 不應被呼叫）。
 */
public class FailingApp implements App {

    private AppContext ctx;

    @Override
    public void onEnable(AppContext context) {
        this.ctx = context;
        throw new RuntimeException("boom");
    }

    @Override
    public void onDisable() {
        ctx.logger().info("failing-disabled");
    }
}
