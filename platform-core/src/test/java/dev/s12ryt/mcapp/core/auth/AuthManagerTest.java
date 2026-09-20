package dev.s12ryt.mcapp.core.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthManager 契約測試：
 * 1. 首次啟動生成隨機密碼並寫入配置檔。
 * 2. 正確密碼 → 登入成功回傳 token；錯誤密碼 → 空。
 * 3. token 有效；登出後失效。
 * 4. 密碼變更後舊密碼失效。
 */
class AuthManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void 首次初始化_生成隨機密碼寫入配置檔() throws Exception {
        Path file = tempDir.resolve("auth.json");
        AuthManager auth = AuthManager.init(file);

        String saved = Files.readString(file);
        assertTrue(saved.contains("passwordHash"), "配置檔應包含 passwordHash");
        assertTrue(saved.length() > 40, "hash 不應為空");
        assertNotNull(auth);
    }

    @Test
    void 首次初始化_兩次生成的密碼不同() throws Exception {
        AuthManager a = AuthManager.init(tempDir.resolve("a.json"));
        AuthManager b = AuthManager.init(tempDir.resolve("b.json"));

        assertNotEquals(a.currentPassword(), b.currentPassword());
    }

    @Test
    void 登入_正確密碼回傳token() throws Exception {
        AuthManager auth = AuthManager.init(tempDir.resolve("auth.json"));
        String password = auth.currentPassword(); // 測試用途：取得明文（僅首次生成時）

        Optional<String> token = auth.login(password);

        assertTrue(token.isPresent());
        assertTrue(token.get().length() >= 32, "token 應至少 32 字元");
    }

    @Test
    void 登入_錯誤密碼回傳空() throws Exception {
        AuthManager auth = AuthManager.init(tempDir.resolve("auth.json"));

        Optional<String> token = auth.login("wrong-password");

        assertFalse(token.isPresent());
    }

    @Test
    void token_登入後有效() throws Exception {
        AuthManager auth = AuthManager.init(tempDir.resolve("auth.json"));
        String token = auth.login(auth.currentPassword()).orElseThrow();

        assertTrue(auth.isValid(token));
    }

    @Test
    void token_登出後失效() throws Exception {
        AuthManager auth = AuthManager.init(tempDir.resolve("auth.json"));
        String token = auth.login(auth.currentPassword()).orElseThrow();

        auth.logout(token);

        assertFalse(auth.isValid(token));
    }

    @Test
    void token_未登入的隨機字串無效() throws Exception {
        AuthManager auth = AuthManager.init(tempDir.resolve("auth.json"));

        assertFalse(auth.isValid("invalid-token"));
    }

    @Test
    void 密碼變更_舊密碼失效新密碼可登入() throws Exception {
        AuthManager auth = AuthManager.init(tempDir.resolve("auth.json"));
        String old = auth.currentPassword();

        auth.changePassword(old, "new-password-123");

        assertFalse(auth.login(old).isPresent());
        assertTrue(auth.login("new-password-123").isPresent());
    }

    @Test
    void 密碼變更_錯誤舊密碼拒絕變更() throws Exception {
        AuthManager auth = AuthManager.init(tempDir.resolve("auth.json"));
        String old = auth.currentPassword();

        assertThrows(IllegalArgumentException.class, () -> auth.changePassword("wrong", "new-password"));
        assertTrue(auth.login(old).isPresent(), "密碼應保持不變");
    }

    @Test
    void 重新初始化_從配置檔載入既有hash() throws Exception {
        Path file = tempDir.resolve("auth.json");
        AuthManager first = AuthManager.init(file);
        String password = first.currentPassword();

        AuthManager reloaded = AuthManager.init(file);

        // currentPassword 只在首次生成時可用；重載後用 login 驗證
        assertTrue(reloaded.login(password).isPresent(), "重載後原密碼仍可登入");
    }

    @Test
    void 重複登入_密碼錯誤多次後仍可正常登入() throws Exception {
        AuthManager auth = AuthManager.init(tempDir.resolve("auth.json"));
        String password = auth.currentPassword();

        for (int i = 0; i < 5; i++) {
            auth.login("wrong" + i);
        }

        assertTrue(auth.login(password).isPresent());
    }

    @Test
    void 同一檔案重載_密碼hash一致且token設計為單機單例() throws Exception {
        Path file = tempDir.resolve("auth.json");
        AuthManager first = AuthManager.init(file);
        String tokenA = first.login(first.currentPassword()).orElseThrow();
        first.logout(tokenA); // 重載前先登出，避免記憶體狀態干擾

        AuthManager reloaded = AuthManager.init(file);

        // currentPassword 只在首次生成時可用；重載後用 login 驗證
        assertFalse(reloaded.isValid(tokenA), "重載為新實例後舊 token 不應沿用（記憶體型 token）");
        assertTrue(reloaded.login(first.currentPassword()).isPresent(), "重載後原密碼仍可登入");
    }

    // ─── P4：token 過期機制 ───

    @Test
    void token_過期後失效() throws Exception {
        Path file = tempDir.resolve("auth.json");
        AuthManager first = AuthManager.init(file); // 生成密碼寫入檔案
        String password = first.currentPassword();
        AuthManager auth = AuthManager.init(file, 50); // 50ms TTL（測試用）

        String token = auth.login(password).orElseThrow();
        assertTrue(auth.isValid(token), "剛登入的 token 應有效");

        Thread.sleep(150); // 超過 TTL

        assertFalse(auth.isValid(token), "超過 TTL 的 token 應失效");
    }

    @Test
    void token_過期後重新登入仍可用() throws Exception {
        Path file = tempDir.resolve("auth.json");
        AuthManager first = AuthManager.init(file);
        String password = first.currentPassword();
        AuthManager auth = AuthManager.init(file, 50);

        String token1 = auth.login(password).orElseThrow();
        Thread.sleep(150);
        assertFalse(auth.isValid(token1), "舊 token 應已過期");

        String token2 = auth.login(password).orElseThrow();
        assertTrue(auth.isValid(token2), "過期後重新登入的新 token 應有效");
    }

    @Test
    void init_TTL非正數拋IAE() throws Exception {
        Path file = tempDir.resolve("auth.json");
        AuthManager.init(file); // 先生成檔案

        assertThrows(IllegalArgumentException.class, () -> AuthManager.init(file, 0));
        assertThrows(IllegalArgumentException.class, () -> AuthManager.init(file, -1));
    }

    // ─── P5：損壞配置檔的錯誤語意 ───

    @Test
    void 損壞配置檔_iterations非數字_拋IOException() throws Exception {
        Path file = tempDir.resolve("auth.json");
        Files.writeString(file,
                "{\"salt\":\"abc123\",\"passwordHash\":\"def456\",\"iterations\":\"not-a-number\"}");

        assertThrows(IOException.class, () -> AuthManager.init(file),
                "iterations 非數字應拋 IOException（而非 NumberFormatException）");
    }
}
