package dev.s12ryt.mcapp.core.app;

import dev.s12ryt.mcapp.api.AppContext;
import dev.s12ryt.mcapp.api.AppSpec;

/**
 * 平台對 App 的服務接縫。
 *
 * <p>由 platform-core 注入；AppContainer 透過此介面建立/銷毀 App 的執行環境（context），
 * 而不直接依賴 Bukkit/Javalin 等具體實作（便於測試隔離）。
 */
public interface AppServices {

    /**
     * 為 App 建立執行 context。
     *
     * @param spec           App 元資料
     * @param appClassLoader App 專屬 ClassLoader
     * @return 已接線所有平台能力的 AppContext
     */
    AppContext createContext(AppSpec spec, ClassLoader appClassLoader);

    /**
     * 銷毀 App 的 context（關閉 storage、scheduler、logger 等）。
     *
     * @param spec App 元資料
     */
    void destroyContext(AppSpec spec);
}
