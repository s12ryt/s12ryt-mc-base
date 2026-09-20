# 需求確認記錄（TDD 驗收標準）

> 原始需求：做一個以 MC 的 plugin.jar 和 server.jar 形式存在的多應用平台，用於存放小項目。Server 基底使用 Paper 1.21.4。

## 使用者決策記錄

| 決策項 | 使用者選擇 |
| --- | --- |
| 平台架構 | 單一 plugin + 內部模組系統（類 Docker 容器平台）；提供管理員密碼登入 Web 控制台；**不要有任何遊戲內指令** |
| 小項目類型 | MC 遊戲之外的小項目（App 與遊戲內容無關） |
| 構建工具 | Gradle（Kotlin DSL） |
| 存儲 | SQLite（本地資料庫） |
| App 生命週期 | **需要運行時熱加載/卸載（動態載入 App）** |
| Web 後端 | Javalin 6（內嵌於 plugin.jar） |
| Web 前端 | Vue 3 + Vite（構建產物打包進 plugin.jar） |
| 平台能力範圍 | 掛載 Web 路由、獨立 SQLite 存儲、日誌收集、定時任務排程、存取 Bukkit API |
| App 依賴形態 | 可自由使用 Bukkit API（App 能拿到 org.bukkit.Server） |

## 驗收標準（TDD 目標）

### A. 平台核心（plugin.jar）
1.  作為 Paper 1.21.4 插件啟動/關閉，不註冊任何遊戲內指令。
2.  內嵌 Javalin Web 服務（可配置埠號），啟動於插件 onEnable、關閉於 onDisable。
3.  管理員密碼登入：
    -   密碼存於配置（首次隨機生成寫入 config.yml）。
    -   POST /api/auth/login 密碼正確 → 回傳 token；錯誤 → 401。
    -   受保護 API 未帶有效 token → 401。
    -   token 可登出失效。
4.  App 管理：
    -   掃描 `apps/` 目錄中的 `.jar`，動態載入（URLClassLoader 隔離）。
    -   支援運行時啟用/停用/重載（Web 控制台觸發，不需重啟伺服器）。
    -   卸載時清理：該 App 的 scheduler 任務、Bukkit listener、Web 路由、ClassLoader。
5.  App 能力（透過 AppContext 提供）：
    -   `router()`：掛載 `/apps/{appId}/...` 下的自定義 Web 路由（GET/POST/PUT/DELETE）。
    -   `storage()`：每個 App 獨立 SQLite 檔案（類 Docker volume，位於 `apps/{appId}/data.db`）。
    -   `logger()`：獨立日誌流（ring buffer + 檔案），Web 控制台可即時查看。
    -   `scheduler()`：定時任務（非同步執行緒 + 任意務可跳回 Bukkit 主線程執行）。
    -   `bukkit()`：存取 org.bukkit.Server；代註冊 listener 並於卸載時自動清理。
6.  App 完全崩潰（onEnable 拋例外）不得影響平台與其他 App。

### B. App 開發 API（platform-api 模組）
1.  App 只需依賴 platform-api（不強迫依賴 Javalin/Jetty），實作 `App` 介面。
2.  jar 內以 `app.properties`（`app.id`、`app.main`、`app.version`、`app.name`）宣告。
3.  提供簡化 HTTP 抽象（AppHttpRequest/AppHttpResponse），不暴露 Javalin 類型。

### C. Web 控制台（Vue 3 + Vite）
1.  登入頁（密碼輸入）。
2.  App 列表頁：顯示各 App 狀態（RUNNING/STOPPED/FAILED）、啟動/停止/重載按鈕。
3.  App 日誌查看日誌查看頁（輪詢讀取即可）。
4.  構建產物（dist/）打包進 plugin.jar 資源，由 Javalin 直接提供靜態檔案。

### D. 構建與產出
1.  Gradle Kotlin DSL 多模組：`platform-api`、`platform-core`、`apps/hello-app`（示範 App）。
2.  `gradlew build` 產出單一 `platform-core-*.jar`（內嵌依賴與前端資源，shade）。
3.  單元/整合測試全部通過（JUnit 5）。

### 明確排除
-   任何遊戲內指令（/xxx）。
-   MySQL（SQLite 已足夠，之後可擴展）。

## 追加需求（2026-09-20）：全 mod 平台支持

> 用戶原話：「我不是說還要有mod版本嗎」→ 確認：「全mod平台支持」

| 決策項 | 用戶選擇 |
| --- | --- |
| 加載器範圍 | 全平台支持：Paper（既有）+ Fabric + NeoForge + Forge |
| 觸發方式 | tag v* 觸發正式 Release + workflow_dispatch 手動（沿用 Release workflow） |

### 追加驗收標準（TDD 目標）
1. **Phase A — ServerAdapter SPI 重構**：
   - platform-core 抽出 `ServerAdapter` SPI（介面），隔離所有加載器特定 API（Bukkit Plugin/Fabric Server/NeoForge Server）。
   - `DefaultServerAdapter`（無伺服器環境：runOnMainThread 直接執行）+ `BukkitServerAdapter`（Bukkit scheduler 跳主線程）。
   - `AppSchedulerImpl`/`PlatformAppServices`/`PlatformBootstrap` 的 `Supplier<Plugin>` 改為 `Supplier<ServerAdapter>`。
   - `S12rytPlugin` 傳入 `BukkitServerAdapter`；Paper 端行為完全不變（既有 263 測試全綠）。
   - platform-api `AppContext.getServer()` 改回傳 `Object`（避免 Fabric/NeoForge 端 classpath 無 org.bukkit.Server 導致 NoClassDefFoundError）。
2. **Phase B — platform-fabric**：Fabric Loom 構建；`DedicatedServerModInitializer` 進入點；ServerLifecycleEvents SERVER_STARTING/STOPPING 驅動 PlatformBootstrap；資料目錄 `config/s12ryt-mc-base/`；fabric.mod.json（server entrypoint；依賴 fabric-api）；WebServer 與 App 運行於伺服器執行緒外。
3. **Phase C — platform-neoforge**：ModDevGradle 構建；`@Mod` 進入點；NeoForge.EVENT_BUS ServerStartingEvent/ServerStoppingEvent；資料目錄 `config/s12ryt-mc-base/`；neoforge.mods.toml。
4. **Phase D — platform-forge**：ForgeGradle 與 Gradle 9 兼容性評估；不兼容時採獨立嵌套構建（自己的 Gradle 8 wrapper）；`@Mod` + MinecraftForge.EVENT_BUS；mods.toml。
5. **Phase E — 構建與發佈**：CI 建置全部 4 個 jar；release.yml 上傳 4 個 jar 到 Release；三語 README 更新平台支持表。
6. **App 兼容性約束**：App 透過 platform-api 編譯，不直接依賴加載器 API；`getServer()` 回傳 Object（App 自行 instanceof 轉型到目標平台類型）。
