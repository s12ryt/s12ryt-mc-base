package dev.s12ryt.apps.hello;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import dev.s12ryt.mcapp.api.App;
import dev.s12ryt.mcapp.api.AppContext;
import dev.s12ryt.mcapp.api.AppHttpRequest;
import dev.s12ryt.mcapp.api.AppHttpResponse;
import dev.s12ryt.mcapp.api.AppRouter;

/**
 * 示範 App：展示平台所有能力（Web 路由、SQLite storage、scheduler、logger）。
 *
 * <h3>路由</h3>
 * <ul>
 *   <li>{@code GET /ping} → {@code {"pong":true}}</li>
 *   <li>{@code GET /info} → App 資訊 JSON</li>
 *   <li>{@code POST /echo} → 回傳 body</li>
 *   <li>{@code GET /count} → 從 SQLite 讀取訪問計數</li>
 *   <li>{@code POST /count/increment} → SQLite 計數 +1</li>
 * </ul>
 *
 * <h3>SQLite</h3>
 * <p>onEnable 時建立 {@code visits} 表（id INTEGER PRIMARY KEY, count INTEGER）。
 *
 * <h3>Scheduler</h3>
 * <p>onEnable 時啟動 heartbeat 定時任務（每 5 秒記錄日誌）。
 */
public class HelloApp implements App {

    private AppContext context;

    @Override
    public void onEnable(AppContext context) throws Exception {
        this.context = context;

        // 記錄啟動日誌
        context.logger().info("HelloApp started");

        // 建立 visits 表
        try (Connection conn = context.storage().connection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS visits (" +
                    "  id INTEGER PRIMARY KEY, " +
                    "  count INTEGER DEFAULT 0" +
                    ")");
            // 確保有初始行
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) AS c FROM visits")) {
                rs.next();
                if (rs.getInt("c") == 0) {
                    stmt.executeUpdate("INSERT INTO visits (id, count) VALUES (1, 0)");
                }
            }
        }

        // 註冊路由
        AppRouter router = context.router();
        registerRoutes(router);

        // 啟動 heartbeat 排程任務（每 5 秒）
        context.scheduler().scheduleAtFixedDelay(0, 5000, () -> {
            context.logger().info("heartbeat");
        });

        context.logger().info("HelloApp ready: routes registered, heartbeat started");
    }

    @Override
    public void onDisable() {
        if (context != null) {
            context.logger().info("HelloApp stopping");
        }
    }

    // ─── 路由註冊 ───

    private void registerRoutes(AppRouter router) {
        // GET /ping → {"pong":true}
        router.get("/ping", req -> AppHttpResponse.json("{\"pong\":true}"));

        // GET /info → App 資訊
        router.get("/info", req -> {
            String json = String.format(
                    "{\"id\":\"%s\",\"name\":\"%s\",\"version\":\"%s\"}",
                    context.spec().id(),
                    context.spec().name(),
                    context.spec().version());
            return AppHttpResponse.json(json);
        });

        // POST /echo → 回傳 body
        router.post("/echo", req -> {
            String body = req.bodyAsString();
            return AppHttpResponse.text(body);
        });

        // GET /count → 訪問計數
        router.get("/count", req -> {
            int count = getVisitCount();
            return AppHttpResponse.json("{\"count\":" + count + "}");
        });

        // POST /count/increment → 計數 +1
        router.post("/count/increment", req -> {
            int newCount = incrementVisitCount();
            return AppHttpResponse.json("{\"count\":" + newCount + "}");
        });
    }

    // ─── Storage helper ───

    private int getVisitCount() throws Exception {
        try (Connection conn = context.storage().connection();
             PreparedStatement ps = conn.prepareStatement("SELECT count FROM visits WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("count");
            }
            return 0;
        }
    }

    private int incrementVisitCount() throws Exception {
        try (Connection conn = context.storage().connection();
             PreparedStatement update = conn.prepareStatement(
                     "UPDATE visits SET count = count + 1 WHERE id = 1");
             PreparedStatement select = conn.prepareStatement(
                     "SELECT count FROM visits WHERE id = 1")) {
            update.executeUpdate();
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        }
        return -1;
    }
}
