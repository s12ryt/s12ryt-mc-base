package dev.s12ryt.mcapp.api;

/**
 * App 運行上下文：平台提供給每個 App 的能力入口。
 *
 * <p>所有能力皆為惰性取得（lazy），實作由 platform-core 注入。App 絕不自行 new 這些介面。
 */
public interface AppContext {

    /** 本 App 的元資料。 */
    AppSpec spec();

    /** 掛載 Web 路由（前綴 /apps/{appId}/...）。 */
    AppRouter router();

    /** 本 App 的獨立 SQLite 存儲（檔案位於 apps/{appId}/data.db）。 */
    AppStorage storage();

    /** 本 App 的獨立日誌流。 */
    AppLogger logger();

    /** 定時任務排程器（非同步執行緒池；可跳回主線程執行）。 */
    AppScheduler scheduler();

    /**
     * 伺服器實例。
     *
     * <p>Paper 端為 org.bukkit.Server（即 Bukkit.getServer()）；Fabric/NeoForge/Forge 端為各自平台類型。
     * 回傳型別刻意為 Object，避免 App jar 在未編譯對應平台 API 時載入此介面觸發 NoClassDefFoundError；
     * App 應自行 instanceof 判斷後轉型。
     * 單元測試環境無伺服器時為 null。
     */
    Object getServer();

    /** App 私有資料目錄（apps/{appId}/data/），已保證存在。 */
    java.nio.file.Path dataDirectory();

    /** App 儲存至平台的配置實例（apps/{appId}/config.yaml）。 */
    AppConfig config();
}
