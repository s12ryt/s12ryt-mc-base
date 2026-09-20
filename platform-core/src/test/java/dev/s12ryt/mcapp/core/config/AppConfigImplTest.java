package dev.s12ryt.mcapp.core.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AppConfigImpl")
class AppConfigImplTest {

    @Nested
    @DisplayName("Construction")
    class Construction {
        @Test
        @DisplayName("從 Properties 建構")
        void fromProperties() {
            Properties props = new Properties();
            props.setProperty("name", "hello");
            props.setProperty("port", "8080");
            props.setProperty("debug", "true");

            AppConfigImpl config = new AppConfigImpl(props);
            assertEquals("hello", config.getString("name"));
            assertEquals(8080, config.getInt("port", 0));
            assertTrue(config.getBoolean("debug", false));
        }

        @Test
        @DisplayName("null Properties 拋 NPE")
        void nullPropertiesThrows() {
            assertThrows(NullPointerException.class, () -> new AppConfigImpl(null));
        }

        @Test
        @DisplayName("空 Properties 不拋")
        void emptyProperties() {
            AppConfigImpl config = new AppConfigImpl(new Properties());
            assertNull(config.getString("missing"));
            assertFalse(config.containsKey("missing"));
        }
    }

    @Nested
    @DisplayName("從檔案載入")
    class FromFile {
        @Test
        @DisplayName("從 properties 檔案載入")
        void loadFromFile(@TempDir Path tmp) throws IOException {
            Path file = tmp.resolve("app.properties");
            Properties props = new Properties();
            props.setProperty("title", "My App");
            props.setProperty("retries", "3");
            props.setProperty("enabled", "false");
            try (var out = Files.newBufferedWriter(file)) {
                props.store(out, "test config");
            }

            AppConfigImpl config = AppConfigImpl.load(file);
            assertEquals("My App", config.getString("title"));
            assertEquals(3, config.getInt("retries", 0));
            assertFalse(config.getBoolean("enabled", true));
        }

        @Test
        @DisplayName("檔案不存在時回空 config")
        void missingFileReturnsEmpty(@TempDir Path tmp) {
            Path file = tmp.resolve("nope.properties");
            AppConfigImpl config = AppConfigImpl.load(file);
            assertEquals("default", config.getString("x", "default"));
            assertFalse(config.containsKey("x"));
        }

        @Test
        @DisplayName("null path 拋 NPE")
        void nullPathThrows() {
            assertThrows(NullPointerException.class, () -> AppConfigImpl.load(null));
        }
    }

    @Nested
    @DisplayName("getString")
    class GetString {
        @Test
        @DisplayName("存在鍵回傳值")
        void existingKey() {
            Properties props = new Properties();
            props.setProperty("name", "hello");
            AppConfigImpl config = new AppConfigImpl(props);
            assertEquals("hello", config.getString("name"));
        }

        @Test
        @DisplayName("不存在鍵回傳 null")
        void missingKeyReturnsNull() {
            AppConfigImpl config = new AppConfigImpl(new Properties());
            assertNull(config.getString("missing"));
        }

        @Test
        @DisplayName("不存在鍵回傳預設值")
        void missingKeyReturnsDefault() {
            AppConfigImpl config = new AppConfigImpl(new Properties());
            assertEquals("def", config.getString("missing", "def"));
        }

        @Test
        @DisplayName("空字串值正確回傳")
        void emptyValue() {
            Properties props = new Properties();
            props.setProperty("empty", "");
            AppConfigImpl config = new AppConfigImpl(props);
            assertEquals("", config.getString("empty"));
            assertTrue(config.containsKey("empty"));
        }
    }

    @Nested
    @DisplayName("getInt")
    class GetInt {
        @Test
        @DisplayName("有效整數")
        void validInt() {
            Properties props = new Properties();
            props.setProperty("port", "9090");
            AppConfigImpl config = new AppConfigImpl(props);
            assertEquals(9090, config.getInt("port", 0));
        }

        @Test
        @DisplayName("無效整數回預設值")
        void invalidIntReturnsDefault() {
            Properties props = new Properties();
            props.setProperty("port", "abc");
            AppConfigImpl config = new AppConfigImpl(props);
            assertEquals(1234, config.getInt("port", 1234));
        }

        @Test
        @DisplayName("不存在鍵回預設值")
        void missingKeyReturnsDefault() {
            AppConfigImpl config = new AppConfigImpl(new Properties());
            assertEquals(42, config.getInt("missing", 42));
        }

        @Test
        @DisplayName("負數整數")
        void negativeInt() {
            Properties props = new Properties();
            props.setProperty("offset", "-100");
            AppConfigImpl config = new AppConfigImpl(props);
            assertEquals(-100, config.getInt("offset", 0));
        }
    }

    @Nested
    @DisplayName("getBoolean")
    class GetBoolean {
        @Test
        @DisplayName("true 值")
        void trueValue() {
            Properties props = new Properties();
            props.setProperty("flag", "true");
            AppConfigImpl config = new AppConfigImpl(props);
            assertTrue(config.getBoolean("flag", false));
        }

        @Test
        @DisplayName("false 值")
        void falseValue() {
            Properties props = new Properties();
            props.setProperty("flag", "false");
            AppConfigImpl config = new AppConfigImpl(props);
            assertFalse(config.getBoolean("flag", true));
        }

        @Test
        @DisplayName("非 true/false 回預設值")
        void invalidValueReturnsDefault() {
            Properties props = new Properties();
            props.setProperty("flag", "yes");
            AppConfigImpl config = new AppConfigImpl(props);
            assertTrue(config.getBoolean("flag", true));
        }

        @Test
        @DisplayName("不存在鍵回預設值")
        void missingKeyReturnsDefault() {
            AppConfigImpl config = new AppConfigImpl(new Properties());
            assertTrue(config.getBoolean("missing", true));
        }
    }

    @Nested
    @DisplayName("containsKey")
    class ContainsKey {
        @Test
        @DisplayName("存在鍵回 true")
        void existingKey() {
            Properties props = new Properties();
            props.setProperty("name", "hello");
            AppConfigImpl config = new AppConfigImpl(props);
            assertTrue(config.containsKey("name"));
        }

        @Test
        @DisplayName("不存在鍵回 false")
        void missingKey() {
            AppConfigImpl config = new AppConfigImpl(new Properties());
            assertFalse(config.containsKey("missing"));
        }

        @Test
        @DisplayName("null 鍵回 false")
        void nullKey() {
            AppConfigImpl config = new AppConfigImpl(new Properties());
            assertFalse(config.containsKey(null));
        }
    }
}
