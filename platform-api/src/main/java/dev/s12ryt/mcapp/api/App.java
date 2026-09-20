package dev.s12ryt.mcapp.api;

/**
 * App 生命週期介面。
 *
 * <p>實作類需提供 public 無參建構子。平台以反射建構並注入 {@link AppContext}。
 */
public interface App {

    /**
     * App 啟動。
     *
     * @throws Exception 啟動失敗；拋出時平台會將 App 標記為 FAILED 並釋放其資源，
     *                   不影響平台與其他 App。
     */
    void onEnable(AppContext context) throws Exception;

    /** App 停止（平台卸載時回調；實作應儘快返回，不得拋出例外影響清理流程）。 */
    void onDisable();
}
