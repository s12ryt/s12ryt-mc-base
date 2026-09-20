package dev.s12ryt.mcapp.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppSpecTest {

    @Test
    void 合法id包含小寫數字連字符且長度1到64() {
        assertTrue(new AppSpec("hello-app", "n", "1.0", "m").isValidId());
        assertTrue(new AppSpec("a", "n", "1.0", "m").isValidId());
        assertTrue(new AppSpec("a".repeat(64), "n", "1.0", "m").isValidId());
    }

    @Test
    void 不合法id被拒絕() {
        assertFalse(new AppSpec(null, "n", "1.0", "m").isValidId());
        assertFalse(new AppSpec("", "n", "1.0", "m").isValidId());
        assertFalse(new AppSpec("Hello", "n", "1.0", "m").isValidId());
        assertFalse(new AppSpec("has space", "n", "1.0", "m").isValidId());
        assertFalse(new AppSpec("a".repeat(65), "n", "1.0", "m").isValidId());
        assertFalse(new AppSpec("../../../etc", "n", "1.0", "m").isValidId());
    }
}
