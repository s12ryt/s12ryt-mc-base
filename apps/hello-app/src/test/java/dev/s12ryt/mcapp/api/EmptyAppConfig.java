package dev.s12ryt.mcapp.api;

/**
 * 測試用 AppConfig：空配置。
 */
public class EmptyAppConfig implements AppConfig {
    @Override public String getString(String key) { return null; }
    @Override public String getString(String key, String def) { return def; }
    @Override public int getInt(String key, int def) { return def; }
    @Override public boolean getBoolean(String key, boolean def) { return def; }
    @Override public boolean containsKey(String key) { return false; }
}
