package dev.s12ryt.mcapp.core.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理員密碼認證與 token 管理。
 *
 * <p>密碼以 PBKDF2 hash 存於 JSON 檔（salt + hash + iterations）。token 為記憶體型
 * SecureRandom 字串（單機單例設計；重啟或重建實例後失效）。
 */
public final class AuthManager {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int ITERATIONS = 120_000;
    private static final int TOKEN_LENGTH = 32;
    /** token 預設有效期：24 小時。 */
    private static final long DEFAULT_TOKEN_TTL_MILLIS = 24L * 60 * 60 * 1000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORE_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private final Path file;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> validTokens = new ConcurrentHashMap<>();
    private final long tokenTtlMillis;

    private volatile String saltHex;
    private volatile String hashHex;
    private volatile int iterations;
    /** 首次生成時保留明文（僅供配置初始化流程回報給管理員），之後為 null。 */
    private volatile String generatedPassword;

    private AuthManager(Path file, long tokenTtlMillis) {
        this.file = file;
        if (tokenTtlMillis <= 0) {
            throw new IllegalArgumentException("tokenTtlMillis 必須為正數: " + tokenTtlMillis);
        }
        this.tokenTtlMillis = tokenTtlMillis;
    }

    /**
     * 初始化：檔案存在則載入既有 hash；否則生成隨機密碼並寫入。token 有效期為預設 24 小時。
     */
    public static AuthManager init(Path file) throws IOException {
        return init(file, DEFAULT_TOKEN_TTL_MILLIS);
    }

    /**
     * 初始化（自訂 token 有效期）：檔案存在則載入既有 hash；否則生成隨機密碼並寫入。
     *
     * @param file         auth 配置檔路徑
     * @param tokenTtlMillis token 有效期（毫秒），必須為正數
     */
    public static AuthManager init(Path file, long tokenTtlMillis) throws IOException {
        AuthManager manager = new AuthManager(file, tokenTtlMillis);
        manager.loadOrCreate();
        return manager;
    }

    /** 首次生成時回傳明文密碼；非首次（載入既有）回傳 null。 */
    public synchronized String currentPassword() {
        return generatedPassword;
    }

    /** 嘗試登入。成功回傳新 token；失敗回傳空。 */
    public synchronized Optional<String> login(String password) {
        if (password == null || !verify(password)) {
            return Optional.empty();
        }
        byte[] raw = new byte[TOKEN_LENGTH];
        random.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        validTokens.put(token, System.currentTimeMillis());
        return Optional.of(token);
    }

    /** 檢查 token 是否有效（過期 token 自動移除）。 */
    public boolean isValid(String token) {
        if (token == null) {
            return false;
        }
        Long timestamp = validTokens.get(token);
        if (timestamp == null) {
            return false;
        }
        if (System.currentTimeMillis() - timestamp > tokenTtlMillis) {
            // 過期：自動移除
            validTokens.remove(token);
            return false;
        }
        return true;
    }

    /** 登出（token 失效）。未知 token 為 no-op。 */
    public void logout(String token) {
        if (token != null) {
            validTokens.remove(token);
        }
    }

    /** 變更密碼。舊密碼錯誤拋 IllegalArgumentException。 */
    public synchronized void changePassword(String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("新密碼長度至少 8 字元");
        }
        if (oldPassword == null || !verify(oldPassword)) {
            throw new IllegalArgumentException("舊密碼錯誤");
        }
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(newPassword, salt, ITERATIONS, HASH_LENGTH);
        this.saltHex = saltHex(salt);
        this.hashHex = hashHex(hash);
        this.iterations = ITERATIONS;
        this.generatedPassword = null;
        try {
            persist();
        } catch (IOException e) {
            throw new IllegalStateException("無法寫入 auth 配置檔", e);
        }
        validTokens.clear();
    }

    private static String saltHex(byte[] salt) {
        return HexFormat.of().formatHex(salt);
    }

    private static String hashHex(byte[] hash) {
        return HexFormat.of().formatHex(hash);
    }

    private void loadOrCreate() throws IOException {
        if (Files.exists(file)) {
            Map<String, String> store = readStore();
            String salt = store.get("salt");
            String hash = store.get("passwordHash");
            String iters = store.get("iterations");
            if (salt == null || hash == null || iters == null) {
                throw new IOException("auth 配置檔格式錯誤（缺少 salt/passwordHash/iterations）: " + file);
            }
            this.saltHex = salt;
            this.hashHex = hash;
            try {
                this.iterations = Integer.parseInt(iters);
            } catch (NumberFormatException e) {
                throw new IOException("auth 配置檔 iterations 格式錯誤: " + iters, e);
            }
            this.generatedPassword = null;
            return;
        }
        String password = generatePassword();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS, HASH_LENGTH);
        this.saltHex = HexFormat.of().formatHex(salt);
        this.hashHex = HexFormat.of().formatHex(hash);
        this.iterations = ITERATIONS;
        this.generatedPassword = password;
        persist();
    }

    private String generatePassword() {
        String alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private boolean verify(String password) {
        byte[] salt = HexFormat.of().parseHex(saltHex);
        byte[] expected = HexFormat.of().parseHex(hashHex);
        byte[] actual = pbkdf2(password, salt, iterations, expected.length);
        return MessageDigest.isEqual(expected, actual);
    }

    private void persist() throws IOException {
        Map<String, String> store = Map.of(
                "salt", saltHex,
                "passwordHash", hashHex,
                "iterations", String.valueOf(iterations));
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(store), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private Map<String, String> readStore() throws IOException {
        Map<String, String> store = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), STORE_TYPE);
        return store == null ? Map.of() : store;
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations, int length) {
        try {
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    password.toCharArray(), salt, iterations, length * 8);
            javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 計算失敗", e);
        }
    }
}
