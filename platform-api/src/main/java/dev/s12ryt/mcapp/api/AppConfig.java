package dev.s12ryt.mcapp.api;

/**
 * App 配置介面（apps/{appId}/config.yaml）。
 *
 * <p>App 啟動時由平台載入，App 運行期間只讀。
 */
public interface AppConfig {

    /** 讀取字串值。 */
    String getString(String key);

    /** 讀取字串值，不存在時回傳預設值。 */
    String getString(String key, String def);

    /** 讀取整數值。 */
    int getInt(String key, int def);

    /** 讀取布林值。 */
    boolean getBoolean(String key, boolean def);

    /** 鍵是否存在。 */
    boolean containsKey(String key);
}
