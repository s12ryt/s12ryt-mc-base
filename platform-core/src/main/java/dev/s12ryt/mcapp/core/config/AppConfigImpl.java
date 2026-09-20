package dev.s12ryt.mcapp.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

import dev.s12ryt.mcapp.api.AppConfig;

/**
 * App 配置實作（基於 Java Properties）。
 *
 * <p>從 {@link Properties} 或 .properties 檔案載入鍵值。
 * <p>線程安全：Properties 本身是 thread-safe（Hashtable）。
 * <p>配置為只讀快照：載入後不再監聽檔案變化。
 */
public final class AppConfigImpl implements AppConfig {

    private final Properties props;

    /**
     * 從 Properties 建構。
     *
     * @param props 鍵值表（不可為 null）
     */
    public AppConfigImpl(Properties props) {
        this.props = new Properties();
        synchronized (Objects.requireNonNull(props, "props")) {
            this.props.putAll(props);
        }
    }

    /**
     * 從 .properties 檔案載入。
     *
     * <p>檔案不存在時回傳空 config（不拋例外）。
     *
     * @param file .properties 檔案路徑
     * @return 載入的配置（檔案不存在則為空配置）
     */
    public static AppConfigImpl load(Path file) {
        Objects.requireNonNull(file, "file");
        Properties props = new Properties();
        if (Files.exists(file) && Files.isRegularFile(file)) {
            try (var in = Files.newBufferedReader(file)) {
                props.load(in);
            } catch (IOException e) {
                // 載入失敗回空配置（不中斷 App 啟動）
                System.err.println("[AppConfigImpl] 無法載入配置檔案: " + file + " — " + e.getMessage());
            }
        }
        return new AppConfigImpl(props);
    }

    @Override
    public String getString(String key) {
        return props.getProperty(key);
    }

    @Override
    public String getString(String key, String def) {
        return props.getProperty(key, def);
    }

    @Override
    public int getInt(String key, int def) {
        String value = props.getProperty(key);
        if (value == null) {
            return def;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        String value = props.getProperty(key);
        if (value == null) {
            return def;
        }
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        return def;
    }

    @Override
    public boolean containsKey(String key) {
        if (key == null) {
            return false;
        }
        return props.containsKey(key);
    }
}
