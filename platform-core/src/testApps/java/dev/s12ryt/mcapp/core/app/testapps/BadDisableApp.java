package dev.s12ryt.mcapp.core.app.testapps;

import dev.s12ryt.mcapp.api.App;
import dev.s12ryt.mcapp.api.AppContext;

/**
 * onDisable 拋出例外的 App：驗證卸載流程不被 onDisable 例外中斷。
 */
public class BadDisableApp implements App {

    @Override
    public void onEnable(AppContext context) {
        context.logger().info("bad-disable-onEnable");
    }

    @Override
    public void onDisable() {
        throw new IllegalStateException("disable-boom");
    }
}
