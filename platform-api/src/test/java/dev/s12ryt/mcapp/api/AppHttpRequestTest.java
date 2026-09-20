package dev.s12ryt.mcapp.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppHttpRequestTest {

    @Test
    void queryParam_取得單值() {
        var req = new AppHttpRequest("GET", "/x", Map.of(), Map.of("q", List.of("v1", "v2")), new byte[0]);

        assertEquals("v1", req.queryParam("q").orElse(null));
        assertTrue(req.queryParam("missing").isEmpty());
    }

    @Test
    void header_不分大小寫() {
        var req = new AppHttpRequest("GET", "/x", Map.of("X-Custom", List.of("abc")), Map.of(), new byte[0]);

        assertEquals("abc", req.header("x-custom").orElse(null));
        assertEquals("abc", req.header("X-CUSTOM").orElse(null));
    }

    @Test
    void bodyAsString_null主體為空字串() {
        var req = new AppHttpRequest("POST", "/x", Map.of(), Map.of(), null);

        assertEquals("", req.bodyAsString());
    }

    @Test
    void bodyAsString_以utf8解碼() {
        var req = new AppHttpRequest("POST", "/x", Map.of(), Map.of(), "訊息".getBytes());

        assertEquals("訊息", req.bodyAsString());
    }
}
