package dev.s12ryt.mcapp.api;

/**
 * App 元資料（不可變）。
 *
 * @param id      App 唯一識別碼（小寫字母、數字與連字符，例如 "hello-app"）
 * @param name    人類可讀名稱
 * @param version App 版本
 * @param main    App 主類全名（需實作 {@link App}）
 */
public record AppSpec(String id, String name, String version, String main) {

    /** 驗證 id 格式：非空、只允許 [a-z0-9-]，長度 1-64。 */
    public boolean isValidId() {
        return id != null && id.matches("[a-z0-9-]{1,64}");
    }
}
