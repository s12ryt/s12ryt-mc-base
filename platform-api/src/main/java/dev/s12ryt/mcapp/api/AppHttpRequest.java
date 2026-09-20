package dev.s12ryt.mcapp.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 簡化 HTTP 請求（唯讀視圖）。
 *
 * @param method   HTTP 方法（大寫）
 * @param path     完整路徑（例如 /apps/hello-app/ping）
 * @param headers  標頭（鍵小寫）與其值列表
 * @param query    查詢參數
 * @param body     原始請求體（可為空陣列；從未為 null）
 */
public record AppHttpRequest(
        String method,
        String path,
        Map<String, List<String>> headers,
        Map<String, List<String>> query,
        byte[] body) {

    /** 取得單一查詢參數。 */
    public Optional<String> queryParam(String name) {
        return firstOf(query, name);
    }

    /** 取得單一標頭（鍵不分大小寫）。 */
    public Optional<String> header(String name) {
        if (headers == null) {
            return Optional.empty();
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (var e : headers.entrySet()) {
            if (e.getKey().toLowerCase(java.util.Locale.ROOT).equals(lower)) {
                List<String> values = e.getValue();
                return (values == null || values.isEmpty()) ? Optional.empty() : Optional.ofNullable(values.get(0));
            }
        }
        return Optional.empty();
    }

    /** 將 body 以 UTF-8 解碼為字串（空 body 為空字串）。 */
    public String bodyAsString() {
        return body == null ? "" : new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Optional<String> firstOf(Map<String, List<String>> map, String name) {
        if (map == null) {
            return Optional.empty();
        }
        List<String> values = map.get(name);
        return (values == null || values.isEmpty()) ? Optional.empty() : Optional.ofNullable(values.get(0));
    }
}
