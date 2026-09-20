package dev.s12ryt.mcapp.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppHttpResponseTest {

    @Test
    void text_回應包含utf8主體與content_type標頭() {
        AppHttpResponse res = AppHttpResponse.text("你好");

        assertEquals(200, res.status());
        assertEquals("text/plain; charset=utf-8", res.headers().get("content-type"));
        assertArrayEquals("你好".getBytes(java.nio.charset.StandardCharsets.UTF_8), res.body());
    }

    @Test
    void json_回應使用application_json() {
        AppHttpResponse res = AppHttpResponse.json("{\"a\":1}");

        assertEquals(200, res.status());
        assertEquals("application/json; charset=utf-8", res.headers().get("content-type"));
    }

    @Test
    void 建構子_null主體轉為空陣列且標頭鍵統一小寫() {
        AppHttpResponse res = new AppHttpResponse(404, Map.of("Content-Type", "x"), null);

        assertEquals(0, res.body().length);
        assertEquals(404, res.status());
        assertEquals("x", res.headers().get("content-type"));
        assertThrows(UnsupportedOperationException.class, () -> res.headers().put("k", "v"));
    }

    @Test
    void notFound_回傳404與空主體() {
        AppHttpResponse res = AppHttpResponse.notFound();

        assertEquals(404, res.status());
        assertEquals(0, res.body().length);
    }
}
