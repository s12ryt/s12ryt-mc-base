# s12ryt-mc-base

[![CI](https://github.com/s12ryt/s12ryt-mc-base/actions/workflows/ci.yml/badge.svg)](https://github.com/s12ryt/s12ryt-mc-base/actions/workflows/ci.yml)

**簡體中文** | [English](README.en.md) | [繁體中文（主版本）](README.md)

---

以 **Paper 1.21.4 plugin.jar** 形式存在的 **MC 多應用平台**——類 Docker 容器概念，讓你把各種小專案（App）動態放進 Minecraft 伺服器裡運行，每個 App 都有自己獨立的 Web 路由、SQLite 資料庫、日誌與排程器。

> 🎮 **零遊戲內指令**：所有管理操作都透過 Web 控制台完成，不會汙染遊戲內的指令空間。

## ✨ 核心特性

- 🐳 **容器式 App 管理**：`apps/` 目錄掃描、獨立 `URLClassLoader` 隔離、運行時熱載入／卸載／重載
- 🌐 **內嵌 Web 控制台**：Javalin 6 + Vue 3，管理員密碼登入（首次啟動自動生成強密碼）
- 📦 **每 App 獨立資源**：獨立 SQLite（`apps/{id}/data.db`）、獨立日誌（ring buffer + 檔案）、獨立配置檔
- ⏰ **App 能力全開**：Web 路由掛載、定時排程、非同步執行、主線程跳回、可存取 Bukkit API（`org.bukkit.Server`）
- 🛡️ **故障隔離**：單一 App 崩潰不影響平台與其他 App
- 🔐 **PBKDF2 密碼儲存** + 24 小時過期的 Bearer token 認證

## 📂 倉庫結構

```
s12ryt-mc-base/
├── platform-api/      # App 開發者 API（唯一需要依賴的模組）
├── platform-core/     # 平台核心 + Web 控制台（shade 打包成 plugin.jar）
│   └── web-console/   # Vue 3 + Vite 前端
├── apps/hello-app/    # 示範 App（展示全部平台能力）
└── .github/workflows/ # CI：建置 + 263 單元測試 + Paper 煙霧測試
```

## 🚀 快速開始

### 1. 安裝

把 `platform-core/build/libs/s12ryt-mc-base-0.1.0.jar` 放進 Paper 伺服器的 `plugins/` 目錄，然後啟動伺服器。

### 2. 取得管理員密碼

首次啟動時，平台會自動生成 16 字元隨機密碼並印在伺服器日誌：

```
[s12ryt-mc-base] 首次啟動，管理員密碼已生成：XXXXXXXXXXXXXXXX
```

### 3. 登入 Web 控制台

瀏覽器開啟 `http://你的伺服器IP:8080/console/`（埠號可在 `plugins/s12ryt-mc-base/config.yml` 的 `web.port` 修改），輸入密碼即可。

### 4. 放入你的第一個 App

把 App jar（例如 `hello-app-0.1.0.jar`）放到 `plugins/s12ryt-mc-base/apps/` 目錄，重啟伺服器或透過 Web 控制台重載即可。

## 🧑‍💻 開發你自己的 App

### 1. 加入依賴（Gradle Kotlin DSL 為例）

```kotlin
repositories {
    // App 只需要 platform-api（不需要 Javalin / Bukkit）
}
dependencies {
    compileOnly(files("platform-api-0.1.0.jar")) // 或發佈到 maven 後引用
}
```

### 2. 實作 App 介面

```java
public class MyApp implements App {
    @Override
    public void onEnable(AppContext ctx) {
        // Web 路由
        ctx.router().get("/hello", req ->
            AppHttpResponse.json("{\"msg\":\"hi\"}"));

        // SQLite
        try (Connection c = ctx.storage().connection()) {
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS t(id INTEGER)");
        } catch (SQLException ignored) {}

        // 定時任務（非同步）
        ctx.scheduler().scheduleAtFixedDelay(0, 5000, () ->
            ctx.logger().info("heartbeat"));

        // 日誌
        ctx.logger().info("MyApp ready");
    }

    @Override
    public void onDisable() { /* 清理資源 */ }
}
```

### 3. 宣告 app.properties（放在 jar 根目錄）

```properties
app.id=my-app
app.name=My App
app.version=1.0.0
app.main=com.example.MyApp
```

### 4. 部署

jar 丟進 `plugins/s12ryt-mc-base/apps/`，App 路由掛載在 `http://伺服器:8080/apps/my-app/...` 下。

## 🌐 Web 控制台功能

| 頁面 | 功能 |
|------|------|
| 登入頁 | 管理員密碼登入 |
| App 列表 | 顯示各 App 狀態（RUNNING/FAILED）、重載／卸載按鈕 |
| 日誌頁 | 即時輪詢查看 App 日誌（2 秒） |

## 📡 內建 API

| 方法 | 路徑 | 說明 |
|------|------|------|
| GET | `/api/health` | 健康檢查（無需認證） |
| POST | `/api/auth/login` | 登入取得 token（無需認證） |
| POST | `/api/auth/logout` | 登出 |
| POST | `/api/auth/change-password` | 變更密碼 |
| GET | `/api/apps` | App 列表 |
| POST | `/api/apps/{id}/reload` | 重載 App |
| POST | `/api/apps/{id}/unload` | 卸載 App |
| GET | `/api/apps/{id}/logs` | 讀取 App 日誌 |

受保護 API 需帶 `Authorization: Bearer <token>` 標頭。

## 🔨 從原始碼建置

需求：JDK 21+、Node.js 18+（前端建置）

```bash
./gradlew build
# 產出：
#   platform-core/build/libs/s12ryt-mc-base-0.1.0.jar  ← plugin.jar（shade）
#   apps/hello-app/build/libs/hello-app-0.1.0.jar      ← 示範 App
```

## ✅ 品質保證

- **263 個單元測試**全綠（JUnit 5，涵蓋認證、容器、路由、存儲、日誌、排程、Web API）
- **CI 全自動煙霧測試**：GitHub Actions 真實下載 Paper 1.21.4 啟動伺服器，驗證 plugin 載入、App 啟動、Web API 回應

## 📄 技術棧

| 層 | 技術 |
|----|------|
| 基底 | Paper 1.21.4 / Java 21 |
| Web 後端 | Javalin 6.6.0 |
| 前端 | Vue 3 + Vite |
| 存儲 | SQLite（sqlite-jdbc 3.53.2.1） |
| JSON | Gson 2.11.0 |
| 測試 | JUnit 5 |
| 建置 | Gradle Kotlin DSL + Shadow |

## 📄 授權

未指定授權，保留所有權利。

---

**其他語言**：[簡體中文](README.zh-CN.md) | [English](README.en.md)
