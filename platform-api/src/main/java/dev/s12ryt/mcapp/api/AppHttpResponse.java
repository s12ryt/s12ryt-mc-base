package dev.s12ryt.mcapp.api;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 簡化 HTTP 回應。
 *
 * <p>狀態碼固定為 200（OK）、201（Created）、204（No Content）、400、401、403、404、
 * 500。其他狀態碼不受支援（App 應使用明確列舉值之一）。
 */
public record AppHttpResponse(int status, Map<String, String> headers, byte[] body) {

    /** 建構時固定 header 鍵小寫。 */
    public AppHttpResponse {
        headers = headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(lowerCased(headers));
        body = body == null ? new byte[0] : body;
    }

    public static AppHttpResponse ok(byte[] body, String contentType) {
        return new AppHttpResponse(200, Map.of("content-type", contentType), body);
    }

    public static AppHttpResponse text(String body) {
        return new AppHttpResponse(200, Map.of("content-type", "text/plain; charset=utf-8"), body.getBytes(StandardCharsets.UTF_8));
    }

    public static AppHttpResponse json(String json) {
        return new AppHttpResponse(200, Map.of("content-type", "application/json; charset=utf-8"), json.getBytes(StandardCharsets.UTF_8));
    }

    public static AppHttpResponse status(int status) {
        return new AppHttpResponse(status, Map.of(), new byte[0]);
    }

    public static AppHttpResponse notFound() {
        return status(404);
    }

    private static Map<String, String> lowerCased(Map<String, String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        for (var e : headers.entrySet()) {
            if (e.getKey() != null) {
                result.put(e.getKey().toLowerCase(java.util.Locale.ROOT), e.getValue());
            }
        }
        return result;
    }
}
