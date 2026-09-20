package dev.s12ryt.mcapp.core.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.s12ryt.mcapp.api.AppStorage;

/**
 * AppStorageImpl 單元測試。
 *
 * <p>測試變數型別使用 AppStorageImpl（實作類別），因為 close() 是實作層方法
 * （AppStorage 介面只保證 connection()；close() 由平台內部呼叫）。
 * <p>AppStorage 介面的 connection() 和 AppStorageImpl 的 close() 共同構成存儲生命週期管理。
 * <p>驗證：
 * <ul>
 *   <li>connection() 回傳可用連線（建表/寫入/讀取）
 *   <li>每次 connection() 回傳新連線（需手動 close）
 *   <li>db 檔案位於指定目錄下
 *   <li>close() 後 connection() 拋 IllegalStateException
 *   <li>close() 關閉內部資源但不拋例外
 *   <li>close() 冪等
 *   <li>多 App 各自有獨立 db（隔離性）
 *   <li>建構子建立資料目錄
 * </ul>
 */
class AppStorageImplTest {

    @TempDir
    Path tmp;

    private Path appsDir;

    @BeforeEach
    void setUp() {
        appsDir = tmp.resolve("apps");
    }

    @AfterEach
    void tearDown() {
        // @TempDir 自動清理
    }

    // ──────────────────────────────────────────────
    // 建構
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("建構")
    class Construction {

        @Test
        @DisplayName("建構子建立 apps/{appId}/data/ 目錄")
        void constructorCreatesDataDirectory() {
            assertFalse(Files.exists(appsDir));
            new AppStorageImpl(appsDir, "my-app");
            assertTrue(Files.isDirectory(appsDir.resolve("my-app")));
        }

        @Test
        @DisplayName("建構子已存在目錄不拋例外")
        void constructorExistingDirectoryNoError() {
            new AppStorageImpl(appsDir, "app1");
            assertDoesNotThrow(() -> new AppStorageImpl(appsDir, "app2"));
        }

        @Test
        @DisplayName("null appsDirectory 拋 NullPointerException")
        void nullAppsDirThrows() {
            assertThrows(NullPointerException.class, () -> new AppStorageImpl(null, "app1"));
        }

        @Test
        @DisplayName("null appId 拋 NullPointerException")
        void nullAppIdThrows() {
            assertThrows(NullPointerException.class, () -> new AppStorageImpl(appsDir, null));
        }
    }

    // ──────────────────────────────────────────────
    // connection()
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("connection()")
    class ConnectionObtain {

        @Test
        @DisplayName("connection() 回傳非 null 連線")
        void connectionReturnsNonNull() throws SQLException {
            AppStorageImpl storage = new AppStorageImpl(appsDir, "app1");
            try (Connection conn = storage.connection()) {
                assertNotNull(conn);
            }
            storage.close();
        }

        @Test
        @DisplayName("連線可建表、寫入、讀取")
        void connectionCanExecuteDdlDml() throws SQLException {
            AppStorageImpl storage = new AppStorageImpl(appsDir, "app1");
            try (Connection conn = storage.connection()) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("CREATE TABLE t (id INTEGER PRIMARY KEY, name TEXT)");
                    st.executeUpdate("INSERT INTO t VALUES (1, 'hello')");
                    try (ResultSet rs = st.executeQuery("SELECT name FROM t WHERE id=1")) {
                        assertTrue(rs.next());
                        assertEquals("hello", rs.getString("name"));
                        assertFalse(rs.next());
                    }
                }
            }
            storage.close();
        }

        @Test
        @DisplayName("兩次 connection() 回傳不同連線實例")
        void twoConnectionsAreDifferent() throws SQLException {
            AppStorageImpl storage = new AppStorageImpl(appsDir, "app1");
            Connection c1 = storage.connection();
            Connection c2 = storage.connection();
            assertNotSame(c1, c2);
            c1.close();
            c2.close();
            storage.close();
        }

        @Test
        @DisplayName("同一 storage 的連線指向同一 db 檔案（資料可見）")
        void connectionsShareSameDatabase() throws SQLException {
            AppStorageImpl storage = new AppStorageImpl(appsDir, "app1");
            // 第一次連線建表寫入
            try (Connection c1 = storage.connection()) {
                try (Statement st = c1.createStatement()) {
                    st.executeUpdate("CREATE TABLE t (v TEXT)");
                    st.executeUpdate("INSERT INTO t VALUES ('data1')");
                }
            }
            // 第二次連線應能讀到資料
            try (Connection c2 = storage.connection()) {
                try (Statement st = c2.createStatement()) {
                    try (ResultSet rs = st.executeQuery("SELECT v FROM t")) {
                        assertTrue(rs.next());
                        assertEquals("data1", rs.getString("v"));
                    }
                }
            }
            storage.close();
        }

        @Test
        @DisplayName("db 檔案位於 apps/{appId}/data.db")
        void dbFileLocation() throws Exception {
            AppStorageImpl storage = new AppStorageImpl(appsDir, "my-app");
            try (Connection conn = storage.connection()) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("CREATE TABLE x (a INTEGER)");
                }
            }
            Path expectedDb = appsDir.resolve("my-app").resolve("data.db");
            assertTrue(Files.exists(expectedDb), "db 檔案應存在於 " + expectedDb);
            assertTrue(Files.size(expectedDb) > 0, "db 檔案不應為空");
            storage.close();
        }
    }

    // ──────────────────────────────────────────────
    // close()
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("close()")
    class CloseBehavior {

        @Test
        @DisplayName("close() 後 connection() 拋 IllegalStateException")
        void connectionAfterCloseThrows() {
            AppStorageImpl storage = new AppStorageImpl(appsDir, "app1");
            storage.close();
            IllegalStateException ex = assertThrows(IllegalStateException.class, storage::connection);
            assertTrue(ex.getMessage().contains("app1") || ex.getMessage().toLowerCase().contains("closed"),
                "例外訊息應提及 app 或 closed: " + ex.getMessage());
        }

        @Test
        @DisplayName("close() 不拋例外")
        void closeNoException() {
            AppStorageImpl storage = new AppStorageImpl(appsDir, "app1");
            assertDoesNotThrow(storage::close);
        }

        @Test
        @DisplayName("close() 冪等（多次呼叫不拋例外）")
        void closeIdempotent() {
            AppStorageImpl storage = new AppStorageImpl(appsDir, "app1");
            storage.close();
            assertDoesNotThrow(storage::close);
            assertDoesNotThrow(storage::close);
        }

        @Test
        @DisplayName("close() 前已取得的連線仍可使用（不強制關閉）")
        void existingConnectionStillUsableAfterClose() throws SQLException {
            AppStorageImpl storage = new AppStorageImpl(appsDir, "app1");
            Connection conn = storage.connection();
            storage.close();
            // 連線仍可用（平台不主動關閉 App 取得的連線，App 自行管理）
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE TABLE a (x INTEGER)");
            }
            conn.close();
        }
    }

    // ──────────────────────────────────────────────
    // 隔離性
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("App 隔離")
    class Isolation {

        @Test
        @DisplayName("兩個 App 的 storage 各自有獨立 db，互不可見")
        void twoAppsHaveIndependentDatabases() throws SQLException {
            AppStorageImpl s1 = new AppStorageImpl(appsDir, "app1");
            AppStorageImpl s2 = new AppStorageImpl(appsDir, "app2");

            // app1 建表寫入
            try (Connection c1 = s1.connection()) {
                try (Statement st = c1.createStatement()) {
                    st.executeUpdate("CREATE TABLE t (v TEXT)");
                    st.executeUpdate("INSERT INTO t VALUES ('app1-data')");
                }
            }

            // app2 不應有 t 表（獨立 db）
            try (Connection c2 = s2.connection()) {
                try (Statement st = c2.createStatement()) {
                    // app2 的 db 是全新的，不應有 t 表
                    SQLException ex = assertThrows(SQLException.class, () -> st.executeQuery("SELECT * FROM t"));
                    assertNotNull(ex.getMessage()); // no such table
                }
            }

            s1.close();
            s2.close();
        }

        @Test
        @DisplayName("db 檔案各自獨立")
        void dbFilesAreSeparate() throws SQLException {
            AppStorageImpl s1 = new AppStorageImpl(appsDir, "app1");
            AppStorageImpl s2 = new AppStorageImpl(appsDir, "app2");

            // 觸發 db 建檔
            try (Connection c1 = s1.connection(); Connection c2 = s2.connection()) {
                try (Statement st = c1.createStatement()) { st.executeUpdate("CREATE TABLE a(x)"); }
                try (Statement st = c2.createStatement()) { st.executeUpdate("CREATE TABLE b(x)"); }
            }

            Path db1 = appsDir.resolve("app1").resolve("data.db");
            Path db2 = appsDir.resolve("app2").resolve("data.db");
            assertTrue(Files.exists(db1));
            assertTrue(Files.exists(db2));
            assertNotEquals(db1, db2);

            s1.close();
            s2.close();
        }
    }

    // ──────────────────────────────────────────────
    // AutoCloseable
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("AppStorage 實作 AutoCloseable（可用 try-with-resources）")
    void implementsAutoCloseable() {
        AppStorageImpl storage = new AppStorageImpl(appsDir, "app1");
        assertInstanceOf(AutoCloseable.class, storage);
        storage.close();
    }
}
