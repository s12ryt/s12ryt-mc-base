package dev.s12ryt.mcapp.core.spi;

import java.util.Objects;

/**
 * 預設伺服器適配器：無伺服器環境（單元測試、CLI 工具等）下使用。
 *
 * <p>所有任務直接在當前線程同步執行。
 */
public final class DefaultServerAdapter implements ServerAdapter {

    @Override
    public void runOnMainThread(Runnable task) {
        Objects.requireNonNull(task, "task");
        task.run();
    }
}
