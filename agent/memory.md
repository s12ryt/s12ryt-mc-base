# s12ryt-mc-base 操作記錄

## 2026-09-20 — Release workflow（Run#1 驗證成功 + Release 已建立）

### 用戶任務
「應當在CI通過後跑一個構建到releases的workflow吧」→ 觸發策略用戶選擇：**tag 觸發 + 手動按鈕**（tag push 觸發正式 Release + workflow_dispatch 手動觸發）。

### 完成
1. **.github/workflows/release.yml**（commit 0da17ea「ci: 新增 Release workflow（tag v* 觸發 + 手動觸發，建置後建立 GitHub Release 並上傳 jar）」已 push）：
   - 觸發：`push: tags: ['v*']` + `workflow_dispatch`
   - permissions: contents: write
   - job build-and-release（ubuntu-latest, timeout 20min）：Checkout → JDK21 temurin → Node22 → setup-gradle@v4 → npm ci（platform-core/web-console）→ `./gradlew build --no-daemon --stacktrace`（263 測試）→ Verify plugin jar contents（8 條目 + 零指令檢查，同 ci.yml）
   - Determine version：tag 觸發時 VERSION=`${GITHUB_REF_NAME#v}`、release_tag=tag 名、prerelease=false；手動觸發時從根 build.gradle.kts 抓版本（`grep 'version = "' build.gradle.kts | head -n1 | sed 's/.*version = "\(.*\)".*/\1/'`）+ 短 SHA 組 tag=`v{version}-{sha}`、prerelease=true
   - Collect artifacts：cp 兩個 jar + sha256sum > SHA256SUMS.txt
   - Generate release notes（heredoc 中文安裝說明）→ softprops/action-gh-release@v2（generate_release_notes: true, fail_on_unmatched_files: true）
   - Upload release-jars backup（14 days）
2. **手動觸發驗證**（用戶同意）：API POST /actions/workflows/release.yml/dispatches（ref=main）→ Run#1（id 35494301990, event=workflow_dispatch）→ **conclusion=success**。
3. **Release 已建立**：`s12ryt-mc-base v0.1.0-0da17ea`（tag v0.1.0-0da17ea，prerelease=true）→ https://github.com/s12ryt/s12ryt-mc-base/releases/tag/v0.1.0-0da17ea
   - Assets：hello-app-0.1.0.jar（4.2 KB）、s12ryt-mc-base-0.1.0.jar（31,518.1 KB）、SHA256SUMS.txt

### 技術筆記
- workflow 檔案必須先 push 到 main 才能被 GitHub 註冊（API /actions/workflows 確認 state=active）。
- workflow_dispatch 觸發：POST /repos/{owner}/{repo}/actions/workflows/{file}/dispatches，body `{"ref":"main"}`，成功回 204。
- 版本抓取避免 grep -P（CI 環境差異），用 `grep + head + sed` 通用語法。
- 正式發佈流程：`git tag v0.1.0 && git push origin v0.1.0` → tag 觸發 → 正式 Release（非 prerelease）。
- 手動觸發產生的 prerelease（v0.1.0-0da17ea）可保留作驗證紀錄或刪除。

## 2026-09-20 — GitHub repo + CI workflow（Run#7 全綠）

### 用戶任務
「先做一個github-workflow完整的mc各方面測試測試之後丟上github」

### 完成
- Repo：https://github.com/s12ryt/s12ryt-mc-base（public, id 1377933197）
- git init + 身份 s12ryt / 102228212+s12ryt@users.noreply.github.com（非 sisyphus）
- .gitignore 修正：web-console/dist → **/web-console/dist（子目錄 dist 誤 staged）
- .github/workflows/ci.yml：雙 job（build-and-test + paper-smoke-test）
- commits：714d322 初版 → 648cd80 workflow → ddea3bc gradlew 權限 → 9f35e96 npm ci → 5a1bf79 public/.gitkeep → dff72ce Paper v3 API → 95ce69e shadowJar EXCLUDE → 51e673e hello-app 放 apps 目錄

### CI 修復歷程（7 runs）
| Run | 根因 | 修復 |
|-----|------|------|
| 1 | gradlew git mode 100644（Linux 無執行權限） | git update-index --chmod=+x gradlew |
| 2 | npm run build 無 node_modules | ci.yml 兩 job 加 npm ci（working-directory: platform-core/web-console） |
| 3 | web-console/public 不存在（git 不追蹤空目錄） | 提交 public/.gitkeep |
| 4 | PaperMC v2 API sunset（回 {"error":"sunset"}） | 改 v3 API fill.papermc.io/v3/projects/paper/versions/1.21.4/builds/latest → $.downloads["server:default"].url + sha256 校驗 |
| 5 | PluginRemapper「Failed to remap plugin jar」 | shadowJar duplicatesStrategy INCLUDE→EXCLUDE + exclude META-INF 簽章/LICENSE/NOTICE/module-info；本地驗證 jar 9883 entries 0 重複 |
| 6 | hello-app jar 放 plugins/ 被 Bukkit 當 plugin 載入失敗 | hello-app 改放 plugins/s12ryt-mc-base/apps/（PlatformBootstrap 掃描目錄 = getDataFolder().resolve("apps")） |
| **7** | — | **全綠** ✅ |

### Run#7 驗證（id 35492010161, commit 51e673e）
- Build & Unit Tests job：npm ci + gradle build（263 測試）+ jar 內容驗證（8 必要 entry + plugin.yml 無 commands）+ artifacts 全 success
- Paper 1.21.4 Smoke Test job：Download Paper（v3 API+sha256）→ server 啟動 "Done (" → plugin 載入/Enabling → 首次密碼生成 → Javalin 啟動 → hello-app "App started" → health API → console 200 → /apps/hello-app/ping pong → graceful stop 全 success

### 技術筆記
- Paper v3 API：fill.papermc.io/v3/projects/paper/versions/1.21.4/builds/latest（downloads["server:default"].url + checksums.sha256）
- shadowJar mergeServiceFiles 後仍可能 duplicate entries（Kotlin stdlib 多模組）→ EXCLUDE + exclude 清單
- Paper 啟動會對 plugin jar 跑 PluginRemapper，duplicate entries 直接失敗
- 平台 App jar 不是 Bukkit plugin：放 plugins/{plugin-name}/apps/，且須在 server 首啟前就位（onEnable 時掃描）
- CI log 抓取：git credential fill 拿 token → Invoke-WebRequest jobs/{id}/logs Bearer
- commit 身份規則：拒絕 sisyphus-dev-ai / sisyphus@mengmota.com

---

## 2026-09-20 — Ralph Loop：25 輪排查 + 修復（263 測試全綠）

### 用戶任務（Ralph Loop）
「請執行25輪次的深度查bug之後用25輪次修正bug並順便查還有無其他因為修bug而多出的bug」

### 排查階段（25 輪，涵蓋全部原始碼）
逐檔全文審查：AppRouterRegistry、AppContainer、AuthManager、AppStorageImpl、AppLoggerImpl、AppSchedulerImpl、AppConfigImpl、AppContextImpl、PlatformAppServices、PlatformBootstrap、WebServer、HelloApp、測試基礎設施、構建配置。

### 發現問題清單（P1-P14，8 修復 / 6 判定不修）
| # | 嚴重度 | 位置 | 問題 | 處置 |
|---|--------|------|------|------|
| P1 | 🔴 | AppRouterRegistry.compilePattern | `{name}` 無驗證：空名/數字開頭/重複名 → PatternSyntaxException 炸 App onEnable | ✅ 修復 |
| P2 | 🟡 | AppRouterImpl.register | clear 後 router 仍可重註冊 | ❌ 不修（既定語意，有測試覆蓋） |
| P3 | 🟡 | AppContainer.close | 未 synchronized，與併發 load 競態可能漏卸載 | ✅ 修復 |
| P4 | 🟠 | AuthManager.isValid | validTokens 時間戳從未使用，token 永久有效 | ✅ 修復（24h TTL） |
| P5 | 🟡 | AuthManager.loadOrCreate | 損壞檔案 parseInt 拋 NumberFormatException 而非 IOException | ✅ 修復 |
| P6 | 🟡 | AuthManager.ioLock | 死代碼欄位 | ✅ 刪除 |
| P7 | 🟡 | AppStorageImpl | appId 不驗證格式 | ❌ 不修（上層 AppSpec.isValidId 已擋，過度防禦） |
| P8 | 🟡 | AppLoggerImpl.recentLines | skip 迴圈 O(n) 效率 | ❌ 不修（非 bug） |
| P9 | 🟡 | AppLoggerImpl 建構子 | touch file 併發 FileAlreadyExistsException | ❌ 不修（每 App 單實例） |
| P10 | 🟡 | AppSchedulerImpl.runOnMainThread | plugin null 時當前線程執行 | ❌ 不修（測試環境 fallback，生產恆非 null） |
| P11 | 🟡 | AppSchedulerImpl.runAsync | ensureOpen 在 try 外 | ❌ 不修（邏輯正確） |
| P12 | 🔴 | WebServer | GET /console（無尾斜線）404，無法進入控制台 | ✅ 修復 |
| P13 | 🟠 | WebServer.buildAppHttpRequest | ctx.bodyAsBytes() 調用兩次，流式 body 重讀風險 | ✅ 修復 |
| P14 | 🟡 | WebServer GSON | 死代碼欄位 | ✅ 刪除 |

### 修復明細（TDD RED→GREEN）
1. **P12**：WebServer.java 加 `app.get("/console", ctx -> ctx.redirect("/console/"))`。RED：第一版斷言 `isIn(301,302)` 失敗（OkHttp 自動跟隨 redirect 得 200）→ 改用獨立 `OkHttpClient.Builder().followRedirects(false)` + `server.port()` 直連驗證 302 + Location=/console/。WebServerTest 21/21。
2. **P1**：compilePattern 加驗證——`name.matches("[A-Za-z][A-Za-z0-9]*")` 失敗拋 IAE「路由參數名無效」（**Java 正則組名不允許底線**，故排除 `_`）；`paramNames.contains(name)` 拋 IAE「路由參數名重複」。未閉合 `{` 當字面語意保留。AppRouterRegistryTest 新增 @Nested PatternValidation 7 測試（空名/數字開頭/含空格/底線/重複名拋 IAE；未閉合{字面；合法名匹配+pathParams），25/25。
3. **P4**：AuthManager 新增 `DEFAULT_TOKEN_TTL_MILLIS = 24h`；構造子 `AuthManager(Path, long tokenTtlMillis)`（<=0 拋 IAE）；`init(Path)` 委託 `init(Path, long)`；isValid 改為 get→timestamp null→false、過期則 remove+false。4 新測試：過期失效（50ms TTL + sleep 150ms）、過期後重新登入可用、TTL<=0 拋 IAE、損壞檔案。AuthManagerTest 16/16。
4. **P5**：loadOrCreate 的 parseInt 包 try-catch NumberFormatException → IOException「auth 配置檔 iterations 格式錯誤」。1 新測試（寫入 `{"iterations":"not-a-number"}` 斷言 IOException）。
5. **P3**：AppContainer.close() 全包 synchronized(lifecycleLock)（unload 內部可重入安全；close 期間併發 load 被阻擋）。
6. **P13**：`byte[] raw = ctx.bodyAsBytes(); byte[] body = raw != null ? raw : new byte[0];`。
7. **P6/P14**：刪除 ioLock / GSON 死代碼。

### 測試數變化：251 → 263
- platform-core：AuthManager 16（+4）、AppRouterRegistry 25（+7）、WebServer 21（+1）、其餘不變
- XML 統計驗證：**TOTAL=263 FAILURES=0 ERRORS=0**

### 修復引入新 bug 檢查（針對性複查全部 8 個修改點）
- P1：名稱驗證 + 重複檢查位置正確，未閉合 `{` 語意保留 ✅
- P3：close 全包 lifecycleLock，unload 可重入（Java synchronized 可重入）不死鎖 ✅
- P4：isValid 過期語意正確，生產路徑 init(Path) 用 24h 預設不破壞既有行為 ✅
- P5：IOException 向上傳播至 PlatformBootstrap.startup 的 IllegalStateException 包裝 ✅
- P12：GET /console redirect 不影響 /console/* 靜態服務（路由更精確優先）✅
- P13/P14：編譯 + 既有 21 測試全綠 ✅
- 結論：**無新 bug 引入**（263 全綠 + 逐點複查通過）

### 技術筆記
- Java 正則具名組名規則：`[A-Za-z][A-Za-z0-9]*`，**不允許底線**
- OkHttp 預設 followRedirects=true；測 redirect 需獨立 OkHttpClient
- javap 位置：C:\Program Files\Java\latest\jdk-25\bin\javap.exe

## 2026-09-20 — 深入排查（第二輪複查，251 測試全綠）

### 用戶請求
「再仔細排查看看」

### 發現並修復 5 個問題
1. 🔴 **pathParams 完全遺失**（嚴重）：buildAppHttpRequest 的 pathParams 參數被丟棄，App 拿不到 /items/{id} 的 id。既有測試是假斷言（handler 只回 "ok"，只驗 200）。RED：expected "id=42" but was "id=MISSING"。修復：pathParams 併入 query map（鍵名同 {name}）。測試改真斷言：handler 回顯 req.queryParam("id")。
2. 🔴 **serveWebConsole 路徑遍歷**：/console/../plugin.yml 可讀 classpath 任意資源。修復：拒絕含 ".." 的 path → 404。新增 2 測試：明文遍歷（驗證 body 不洩漏 plugin.yml 內容 s12ryt-mc-base/S12rytPlugin）+ %2e%2e 編碼遍歷。
3. 🟠 **ctx.body().getBytes 損壞二進位**：改 ctx.bodyAsBytes()。測試改用 testtools raw body（OkHttp RequestBody.create(bytes, MediaType)）——client.post(byte[]) 會 JSON 序列化成 "[0,1,255,...]"（RED：len=10 ≠ len=6）。
4. 🟠 **S12rytPlugin 缺 saveDefaultConfig()**：jar 內 config.yml 永遠不會提取，管理員無法改埠號。修復：onEnable 開頭呼叫（例外：需 Bukkit 環境無法單元測試，人工驗證）。
5. 🟡 **requireAuth 重複回應**：ctx.status(401).json 後又 throw UnauthorizedResponse（重複設定）。修復：只 throw UnauthorizedResponse。

### 技術發現
- javalin-testtools 6.6.0 底層是 **OkHttp 4.12.0**：`client.request(path, reqBuilder -> reqBuilder.post(okhttp3.RequestBody.create(bytes, MediaType)))` 發 raw body；`client.post(path, json)` 對 byte[] 做 JSON 序列化（變成 "[0,1,255,..."）。
- Javalin Context：`bodyAsBytes()` 回 byte[]、`result(byte[])` 設回應、`req().getHeaderNames()` 回 Enumeration。
- 測試覆蓋盲點教訓：只斷言狀態碼不驗證內容（假斷言）會掩蓋參數傳遞類 bug；應讓 handler 回顯收到的參數做真斷言。

### TDD 輪次
- RED：`gradlew.bat :platform-core:test --tests WebServerTest` → 18 tests, 2 failed（pathParams MISSING；binary len=10）。
- GREEN：WebServer.java 修 4 處（pathParams 併入 query + bodyAsBytes + 路徑遍歷防禦 + requireAuth 清理）+ S12rytPlugin saveDefaultConfig() → WebServerTest 20/20 通過。
- 全回歸：`gradlew.bat :platform-api:test :platform-core:test :apps:hello-app:test` BUILD SUCCESSFUL，XML 統計 **TOTAL=251 FAILURES=0 ERRORS=0**。

### WebServerTest 最終結構（20 測試）
HealthCheck(1) + AuthApi(6) + AppManagementApi(4) + AppRouteDispatch(6，含 pathParams 真斷言 + binaryBody raw body) + WebConsoleStatic(3，含 2 個路徑遍歷測試)。

## 2026-09-20 — 代碼邏輯複查 + 嚴重 bug 修復（248 測試全綠）

### 用戶請求
「請你複查一下當前的代碼邏輯實現」

### 複查範圍
- platform-api 10 介面：邏輯正確，無問題
- platform-core 生命週期：S12rytPlugin/PlatformBootstrap/AppContainer/PlatformAppServices/AppContextImpl — 正確
- platform-core 功能組件：AuthManager/AppRouterRegistry/AppStorageImpl/AppLoggerImpl/AppSchedulerImpl/AppConfigImpl — 正確
- hello-app 與構建配置 — 正確

### 發現並修復的問題

#### 🔴 嚴重 Bug 1：App 路由成功分派被 Javalin 404 覆蓋
- WebServer 的 `app.before("/apps/{appId}/*")` 分派 handler 成功後沒有 `ctx.skipRemainingHandlers()`，Javalin 找不到 endpoint handler → 404 覆蓋已寫入的 200 回應。所有 App 的 Web 路由實際上永遠回 404。
- 既有測試只測了 404 分支（unknown app），成功分派零覆蓋所以沒抓到。
- 修復：handler 成功/拋例外後都呼叫 `ctx.skipRemainingHandlers()`；無匹配路由改為 `return` 不攔截。

#### 🟠 問題 2：serveWebConsole SPA fallback Content-Type 錯誤
- 無副檔名路徑 fallback 回 index.html 但 Content-Type 沒設 text/html。
- 修復：fallback 時強制 Content-Type text/html。

#### 🟠 問題 3：帶副檔名的缺失資源也 fallback 回 index.html
- /console/assets/missing.js 應回 404 而非 index.html。
- 修復：帶副檔名的缺失資源回 404；無副檔名才 fallback index.html（SPA 路由）。

#### 🟡 問題 4：AppStorageImpl 未使用的 AtomicInteger import
- 修復：刪除。

### TDD 輪次
1. RED：WebServerTest 新增 5 測試（成功分派/方法不匹配/handler 拋例外/路徑參數/console 靜態）→ 3 failed，證實 404 覆蓋 bug。
2. GREEN：加 skipRemainingHandlers() → 17/17 通過。
3. 全回歸：`gradlew.bat :platform-api:test :platform-core:test :apps:hello-app:test` BUILD SUCCESSFUL。
   - XML 統計：**248 測試全綠（0 failures, 0 errors）**（platform-api 10 + platform-core 228 + hello-app 10）。

---

## 2026-09-20 — 專案完成（243 測試全綠 + shade jar 產出）

### 完整 TDD 輪次記錄
| Phase | 模組 | 測試數 | RED → GREEN |
|-------|------|--------|-------------|
| 2 | platform-api | 10 | RED: compileTestJava 失敗 → GREEN: 寫介面實作 |
| 3 | AuthManager | 12 | RED: cannot find symbol → GREEN: 12/12 通過 |
| 4 | AppContainer | 18 | RED: 49 errors → GREEN round 1: 10/18 (jar 鎖+captures) → round 2: 18/18 |
| 5 | AppRouterRegistry | 18 | RED: 10 errors → GREEN round 1: bodyAsString() 修正 → round 2: 18/18 |
| 6 | AppStorageImpl | 16 | RED: 35 errors → GREEN: AppStorage 變數型別修正 → 16/16 |
| 7 | AppLoggerImpl | 24 | RED: 47 errors → GREEN round 1: 23/24 (併發 800>500) → round 2: 24/24 |
| 8 | AppSchedulerImpl | 27 | RED: 7 errors → GREEN: import AtomicBoolean → 27/27 |
| 9 | AppConfigImpl+AppContextImpl | 47 | RED: 52 errors → GREEN: 47/47 |
| 10 | PlatformAppServices | 26 | RED: 9 errors → GREEN round 1: ok(String) 不存在 → round 2: SQLite 鎖 → round 3: 26/26 |
| 11 | PlatformBootstrap | 23 | RED: 7 errors → GREEN: 23/23 |
| 12 | WebServer | 12 | RED: compileTestJava → GREEN: Javalin 6 TestClient API 修正 → 12/12 |
| 14 | hello-app | 10 | RED: 10/10 失敗 → GREEN round 1: InMemoryStorage → round 2: FakeScheduler → round 3: 10/10 |
| **總計** | | **243** | |

### 重構記錄
- AppContext.java getBukkit() → getServer()：Bukkit 是 final 無法實例化
- AppSchedulerImpl：移除 private getPlugin()，新增 Supplier<Plugin> 雙參構造子
- PlatformAppServices：新增 Supplier<Plugin> 構造子 + getLogger(appId)
- build.gradle.kts：javalin → javalin-bundle（含 testtools）；新增 assertj-core；shadow plugin 9.6.1

### Javalin 6 關鍵 API 發現
- Response body 設定：`ctx.result(byte[])` 而非 `ctx.write(byte[])`
- 請求標頭取得：`ctx.req().getHeaderNames()` 返回 Enumeration（需 Collections.list 轉換）
- TestClient：`client.get(path, req -> req.header(name, value))` — header 在 Request.Builder 上
- `client.post(path, json)` — json 參數被 JSON 序列化（傳 Map 變成 JSON object）

### 構建產出
- `gradlew.bat build` BUILD SUCCESSFUL — 243 測試全綠
- shadow jar：platform-core/build/libs/s12ryt-mc-base-0.1.0.jar（含 Javalin+SQLite+Gson+前端+API）
- App jar：apps/hello-app/build/libs/hello-app-0.1.0.jar（app.properties + HelloApp.class）

### 文件更新
- agent/deep_todos.md：Phase 1-15 完整記錄 + 測試統計表
- agent/項目表.md：完整模組結構樹 + 依賴關係 + 各檔案用途表
- agent/memory.md：本文件

---

## 2026-09-20 — AppContainer TDD 完成（歷史記錄）

### 讀取操作
- 讀取 platform-api 全部 8 個介面（App, AppSpec, AppContext, AppRouter, AppLogger, AppScheduler, AppStorage, AppConfig）確認 API 簽名
- 讀取 platform-core/build.gradle.kts 確認 testApps source set 配置
- 讀取 4 個 testApps fixture 類別（RecordingApp, FailingApp, BadDisableApp, NotAnApp）

### 寫入操作
- **建立** platform-core/src/test/java/dev/s12ryt/mcapp/core/app/FakeAppContext.java
  - 測試替身，實作 AppContext 全 8 方法
  - logger 寫入同步 List（含 INFO/WARN/ERROR 前綴）
  - scheduler stub：runAsync/runOnMainThread 直接同步執行
  - getServer 回傳 Bukkit.getServer()（null）
- **建立** platform-core/src/test/java/dev/s12ryt/mcapp/core/app/FakeServices.java
  - 測試替身，實作 AppServices
  - 記錄 createdSpecs/destroyedSpecs/contexts
  - captures 存 FakeAppContext 實例參照（即時讀取 logLines，非快照）
  - setFailCreate(true) 模擬 createContext 失敗
- **建立** platform-core/src/test/java/dev/s12ryt/mcapp/core/app/AppContainerTest.java
  - 18 個測試，涵蓋 loadAll/load/unload/reload/get/list/close
  - 每個載入 App 的測試用 try-with-resources 確保 container.close()
  - helper：appJar/appJarInDir/writeJar/props
- **建立** platform-core/src/main/java/dev/s12ryt/mcapp/core/app/AppServices.java
  - interface { createContext(AppSpec, ClassLoader)→AppContext; destroyContext(AppSpec) }
- **建立** platform-core/src/main/java/dev/s12ryt/mcapp/core/app/AppContainer.java
  - implements AutoCloseable
  - enum AppState { RUNNING, FAILED }
  - record AppHandle(id, name, version, jar, state, error)
  - private record Entry(spec, jar, loader, app, handle)
  - private record RawMeta(id, name, version, main, error) — readMeta 不拋例外
  - private static class AppLoadException — 內部載入失敗信號
  - ConcurrentHashMap<String, Entry> apps + Object lifecycleLock
  - URLClassLoader parent-first，close() 釋放 jar 檔案鎖
- **建立** agent/deep_todos.md — 完整歷史任務記錄
- **建立** agent/項目表.md — 項目結構與依賴關係
- **建立** agent/memory.md — 本文件

### 重構操作
- AppContext.java getBukkit() → getServer()：原回傳型別 Bukkit（final, private ctor）改為 Server

### 測試執行
- `gradlew.bat :platform-core:compileTestJava` — RED：49 errors（cannot find symbol AppContainer/AppServices）
- `gradlew.bat :platform-core:test --tests AppContainerTest` — GREEN round 1：10/18 通過，8 失敗
  - 失敗原因：jar 檔案鎖未釋放（container 未 close）、appJarInDir 缺 putNextEntry、FakeServices captures 存快照非參照
- 修復 FakeServices（captures→contexts 實例參照）+ AppContainerTest（try-with-resources + appJarInDir 修復）
- `gradlew.bat :platform-core:test --tests AppContainerTest` — GREEN round 2：18/18 通過 ✅
- `gradlew.bat :platform-api:test :platform-core:test` — 全回歸 BUILD SUCCESSFUL
  - platform-api 10 + platform-core 30（AuthManager 12 + AppContainer 18）= 40 測試全綠

### TDD 輪次記錄
- RED：compileTestJava 失敗（cannot find symbol AppContainer/AppServices/AppHandle/AppState）
- GREEN round 1：8/18 失敗（jar 鎖、captures 快照問題、appJarInDir bug）
- GREEN round 2：18/18 通過
- 回歸：40/40 全綠
