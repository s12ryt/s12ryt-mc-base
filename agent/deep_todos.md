# s12ryt-mc-base 完整歷史任務記錄

## 專案概述
MC 多應用平台：以 Paper 1.21.4 plugin.jar + server.jar 形式存在的多應用平台。
單一 plugin + 內部模組系統（類 Docker 容器平台），管理員密碼登入 Web 控制台，零遊戲內指令。

## 技術棧
- Java 25.0.2（toolchain 21）、Gradle Kotlin DSL、Paper 1.21.4
- Javalin 6.6.0（javalin-bundle，含 testtools + Jackson + Logback）
- SQLite 3.53.2.1、Gson 2.11.0、JUnit 5.11.4
- Vue 3.5.13 + Vue Router 4.5.0 + Vite 6.0.7
- shadow plugin 9.6.1（com.gradleup.shadow）

## 已完成任務

### Phase 1：環境與骨架
- [x] Gradle Wrapper 9.7.1 手動下載就位（gradlew.bat + gradle/wrapper/*）
- [x] settings.gradle.kts：rootProject.name = "s12ryt-mc-base"；include platform-api, platform-core, apps:hello-app；pluginManagement（gradlePluginPortal + mavenCentral）
- [x] 根 build.gradle.kts：group=dev.s12ryt, version=0.1.0, 全子模組倉庫 mavenCentral+papermc
- [x] gradle.properties：-Xmx2G, parallel, caching
- [x] .gitignore：build/.gradle/node_modules/web-console/dist/run/logs 等

### Phase 2：platform-api 模組（10 測試全綠）
- [x] App.java：interface App { void onEnable(AppContext) throws Exception; void onDisable(); }（public 無參建構子，反射建構）
- [x] AppSpec.java：record(id,name,version,main)；isValidId() 驗證 [a-z0-9-]{1,64}
- [x] AppContext.java：spec()/router()/storage()/logger()/scheduler()/getServer()/dataDirectory()/config()
  - 注意：getServer() 回傳 org.bukkit.Server（原名 getBukkit() 已重構，因 Bukkit 是 final 無法實例化）
- [x] AppRouter.java：get/post/put/delete(pattern, AppHandler)；AppHandler 函數式介面 handle(AppHttpRequest)→AppHttpResponse
- [x] AppHttpRequest.java：record(method,path,headers,query,body[])；queryParam()/header()（不分大小寫）/bodyAsString()（UTF-8）
- [x] AppHttpResponse.java：record(status,headers,byte[] body)；ok(byte[],String)/text(String)/json(String)/status(int)/notFound() 工廠
- [x] AppStorage.java：Connection connection() throws SQLException（每次新連線，App 停止後拋 IllegalStateException）
- [x] AppLogger.java：info/warn/error(message,Throwable)/recentLines(int)（由舊到新）
- [x] AppScheduler.java：scheduleAtFixedDelay/scheduleOnce/cancel/runOnMainThread/runAsync
- [x] AppConfig.java：getString(key[,def])/getInt/getBoolean/containsKey

### Phase 3：platform-core — AuthManager（12 測試全綠）
- [x] AuthManager.init(Path file)：檔案存在載入既有 hash，否則生成 16 字元隨機密碼
- [x] PBKDF2WithHmacSHA256, 120000 iterations, salt 16B, hash 32B；原子寫入（tmp+move）
- [x] currentPassword()：僅首次生成時回傳明文（之後 null）
- [x] login(password)→Optional<String> token（32B SecureRandom hex）
- [x] isValid(token)；logout(token)
- [x] changePassword(old,new)：新密碼>=8 字元，成功後清除所有 token
- [x] token 為記憶體型（ConcurrentHashMap），重載新實例後舊 token 失效

### Phase 4：platform-core — AppContainer（18 測試全綠）
- [x] AppServices.java：interface { createContext(AppSpec, ClassLoader)→AppContext; destroyContext(AppSpec) }
- [x] AppContainer.java：implements AutoCloseable
  - enum AppState { RUNNING, FAILED }
  - record AppHandle(id, name, version, jar, state, error)
  - loadAll/load/unload/reload/get/list/close
  - URLClassLoader parent-first，close() 釋放 jar 檔案鎖（Windows）
  - 載入期失敗→FAILED handle 不入 map；啟用期失敗→FAILED 條目保留
  - unload(FAILED) 僅移除條目（避免雙重釋放）
- [x] testApps source set + 4 fixture（RecordingApp/FailingApp/BadDisableApp/NotAnApp）
- [x] FakeAppContext + FakeServices 測試替身

### Phase 5：platform-core — AppRouterRegistry（18 測試全綠）
- [x] AppRouterRegistry.java：route 註冊與匹配（不依賴 Javalin）
  - createRouter(appId)→AppRouter；routes(appId)→List<RouteEntry>
  - match(method, path)→Optional<RouteMatch>（剝離 /apps/ 前綴 → appId + subPath → 正則匹配）
  - clear(appId)/clearAll()
  - pattern {name} 語法→正則 (?<name>[^/]+)
  - ConcurrentHashMap<String, List<RouteEntryImpl>> + synchronized list
  - AppRouterImpl：get/post/put/delete 註冊到 registry

### Phase 6：platform-core — AppStorageImpl（16 測試全綠）
- [x] AppStorageImpl implements AppStorage, AutoCloseable
  - 每 App 獨立 SQLite：apps/{appId}/data.db
  - connection() 每次 DriverManager.getConnection（不快取連線）
  - close() 設 closed=true（AtomicBoolean），不關閉已發出連線
  - close 後 connection() 拋 IllegalStateException
  - close() 冪等

### Phase 7：platform-core — AppLoggerImpl（24 測試全綠）
- [x] AppLoggerImpl implements AppLogger, AutoCloseable
  - ring buffer（ArrayDeque，上限 500 行）+ 檔案持久化（apps/{appId}.log）
  - 線程安全：ReentrantLock 保護 ring buffer + 檔案寫入
  - close 後不再接受新寫入（volatile + lock double-check），recentLines 仍可讀
  - 日誌行格式：`[yyyy-MM-dd HH:mm:ss] [LEVEL] [appId] message`
  - error 帶 Throwable 時追加 stack trace 行
  - recentLines(maxLines<=0 預設為 200)

### Phase 8：platform-core — AppSchedulerImpl（27 測試全綠）
- [x] AppSchedulerImpl implements AppScheduler, AutoCloseable
  - ScheduledExecutorService（守護線程池，corePoolSize=max(1, cpuCores/2)）
  - scheduleAtFixedDelay/scheduleOnce → executor.schedule*
  - cancel(taskId) → ScheduledFuture.cancel(false)
  - runOnMainThread → Bukkit.getServer()!=null 時 server.getScheduler().runTask(plugin, task)；否則 task.run()
  - runAsync → executor.submit(wrap(task))
  - close() → shutdownNow + tasks.clear
  - **Supplier<Plugin> 注入**：雙參構造子接受 pluginSupplier（原有單參 delegates to `()->null`）

### Phase 9：platform-core — AppContextImpl + AppConfigImpl（47 測試全綠）
- [x] AppConfigImpl：基於 Java Properties，線程安全
  - `new AppConfigImpl(Properties)` + 靜態 `load(Path file)`（不存在/IO 失敗回空 config）
  - getInt 解析失敗回 def；getBoolean 只認 true/false
- [x] AppContextImpl implements AppContext, AutoCloseable
  - 7 參構造子（spec/router/storage/logger/scheduler/dataDirectory/config）全 requireNonNull
  - getServer() → Bukkit.getServer()
  - close() 關閉 storage+logger+scheduler（不關 router，由 AppRouterRegistry.clear 統一清理）

### Phase 10：platform-core — PlatformAppServices（26 測試全綠）
- [x] PlatformAppServices implements AppServices
  - 3 參構造子（appsDir, logDir, routerRegistry）+ 4 參（+, pluginSupplier）
  - createContext：7 步組裝（router/storage/logger/scheduler/dataDirectory/config/AppContextImpl）
  - destroyContext：closeQuietly(context) + routerRegistry.clear(appId)
  - **getLogger(appId)**：從 contexts map 取 AppContextImpl 回傳其 logger()

### Phase 11：platform-core — PlatformBootstrap + S12rytPlugin（23 測試全綠）
- [x] PlatformBootstrap（不依賴 JavaPlugin，可獨立單元測試）
  - 構造子：`PlatformBootstrap(Path dataDirectory)` + `PlatformBootstrap(Path, Supplier<Plugin>)`
  - startup()：建 apps/logs/data 目錄 → AuthManager.init → AppRouterRegistry → PlatformAppServices → AppContainer.loadAll()
  - shutdown()：appContainer.close() + routerRegistry.clearAll()
  - 冪等（started flag）；IOException 包成 IllegalStateException
  - Getter：getAuthManager/getRouterRegistry/getAppContainer/getAppServices/isStarted
- [x] S12rytPlugin extends JavaPlugin（薄封裝層）
  - onEnable：PlatformBootstrap(getDataFolder().toPath(), ()->this) + startup() + WebServer start + 首次密碼輸出
  - onDisable：webServer.close() + bootstrap.shutdown()
- [x] plugin.yml：name=s12ryt-mc-base, version=0.1.0, main=dev.s12ryt.mcapp.core.S12rytPlugin, api-version=1.21, **無 commands 區段**
- [x] config.yml：web.port: 8080

### Phase 12：Web 控制台後端 — WebServer（12 測試全綠）
- [x] WebServer.java：Javalin 6 內嵌
  - GET /api/health（無需認證）→ {"status":"ok"}
  - POST /api/auth/login（無需認證）→ JSON body password → 200+token / 401
  - before("/api/*") 認證 middleware：跳過 health/login，其餘需 Bearer token
  - POST /api/auth/logout / POST /api/auth/change-password
  - GET /api/apps → container.list() → JSON array
  - POST /api/apps/{appId}/reload / POST /api/apps/{appId}/unload
  - GET /api/apps/{appId}/logs → services.getLogger(appId).recentLines()
  - before("/apps/{appId}/*") App 路由分派：registry.match → buildAppHttpRequest → handler.handle → writeAppHttpResponse
  - GET /console/* → 靜態檔案服務（SPA fallback 回 index.html）
  - GET / → redirect /console/
- [x] WebServerTest.java — 12 個 @Test（JavalinTest 整合測試）
  - HealthCheck(1) + AuthApi(6) + AppManagementApi(4) + AppRouteDispatch(1)

### Phase 13：Web 控制台前端 — Vue 3 + Vite（dist 打包進 jar）
- [x] web-console/ 目錄：package.json（Vue 3.5.13 + vue-router 4.5.0 + Vite 6.0.7）
- [x] vite.config.js：base=/console/, proxy /api 和 /apps 到 localhost:8080, outDir=dist/
- [x] src/main.js + App.vue + assets/style.css（暗色主題）
- [x] src/api/index.js：login/logout/changePassword/listApps/reloadApp/unloadApp/getAppLogs/health
- [x] src/views/LoginView.vue + AppsView.vue + LogsView.vue
- [x] build.gradle.kts：buildWebConsole Exec task（npm run build）→ processResources dependsOn + from dist
- [x] WebServer 靜態檔案服務：GET /console/* 從 classpath web-console/ 讀取
- [x] npm install + npm run build 驗證成功

### Phase 14：hello-app 示範 App（10 測試全綠）
- [x] app.properties：app.id=hello-app, app.name=Hello App, app.version=1.0.0, app.main=dev.s12ryt.apps.hello.HelloApp
- [x] HelloApp.java：完整示範平台所有能力
  - onEnable：建 visits 表 + INSERT 初始行 + registerRoutes + scheduler heartbeat(5s) + logger
  - GET /ping → {"pong":true}
  - GET /info → {"id":"hello-app","name":"Hello App","version":"1.0.0"}
  - POST /echo → text(bodyAsString())
  - GET /count → {"count":N}（從 SQLite 讀取）
  - POST /count/increment → {"count":N}（UPDATE+SELECT）
  - onDisable：logger.info("HelloApp stopping")
- [x] 測試 fixture：FakeRouter/FakeAppContext/EmptyAppConfig
  - InMemoryStorage：共享 Connection + Proxy（close() no-op）
  - FakeScheduler：asyncTasks/mainThreadTasks/scheduledTasks
- [x] HelloAppTest.java — 10 個 @Test（Lifecycle 5 + PingRoute 1 + InfoRoute 1 + EchoRoute 1 + CountRoute 1 + IncrementRoute 1）

### Phase 15：整合驗證
- [x] shade 配置：com.gradleup.shadow 9.6.1，archiveBaseName=s12ryt-mc-base, archiveClassifier=""
- [x] `gradlew.bat build` BUILD SUCCESSFUL — 243 測試全綠 + shadowJar 產出
- [x] shadow jar 內容驗證：
  - platform-core/build/libs/s12ryt-mc-base-0.1.0.jar：plugin.yml + config.yml + web-console/* + Javalin + SQLite + Gson + S12rytPlugin + App API
  - apps/hello-app/build/libs/hello-app-0.1.0.jar：app.properties + HelloApp.class

### Phase 16：Ralph Loop — 25 輪深度排查 + 修復（251→263 測試全綠）
- [x] 排查輪 1-25：逐檔全文審查全部原始碼，發現 14 個問題（P1-P14）
- [x] 修復 P1：AppRouterRegistry.compilePattern 驗證 `{name}`（須 [A-Za-z][A-Za-z0-9]*，Java 正則組名不允許底線；重複名拋 IAE；未閉合 `{` 當字面保留）+ 7 新測試（PatternValidation @Nested），AppRouterRegistryTest 25/25
- [x] 修復 P3：AppContainer.close() 全包 synchronized(lifecycleLock)（unload 可重入安全；防併發 load 競態漏卸載）
- [x] 修復 P4：AuthManager token 24h TTL（DEFAULT_TOKEN_TTL_MILLIS；init(Path, long ttlMillis) 重載，ttlMillis<=0 拋 IAE；isValid 過期自動移除）+ 4 新測試，AuthManagerTest 16/16
- [x] 修復 P5：AuthManager.loadOrCreate parseInt 包 try-catch → IOException（損壞配置檔不再拋 NumberFormatException）+ 1 新測試
- [x] 修復 P6：刪除 AuthManager ioLock 死代碼
- [x] 修復 P12：WebServer 新增 `app.get("/console", ctx -> ctx.redirect("/console/"))`（無尾斜線 404 → 302 redirect）+ 1 新測試（獨立 OkHttpClient followRedirects(false) 驗證 302 + Location），WebServerTest 21/21
- [x] 修復 P13：WebServer.buildAppHttpRequest bodyAsBytes() 提取 local var 單次調用（防流式重讀）
- [x] 修復 P14：刪除 WebServer GSON 死代碼
- [x] 判定不修：P2（clear 後重註冊為既定語意）、P7（appId 驗證過度防禦）、P8（recentLines 效率非 bug）、P9（touch file 併發低風險）、P10（plugin null fallback 為測試環境行為）、P11（runAsync ensureOpen 邏輯正確）
- [x] 全回歸：XML 統計 TOTAL=263 FAILURES=0 ERRORS=0（251 基準 + P1 +7 + P12 +1 + P4/P5 +4 = 263）
- [x] 針對性複查 8 修改點：無新 bug 引入

## 測試統計（Phase 16 後）
| 模組 | 測試數 |
|------|--------|
| platform-api | 10 |
| platform-core AuthManager | 16 |
| platform-core AppContainer | 18 |
| platform-core AppRouterRegistry | 25 |
| platform-core AppStorageImpl | 16 |
| platform-core AppLoggerImpl | 24 |
| platform-core AppSchedulerImpl | 27 |
| platform-core AppConfigImpl + AppContextImpl | 47 |
| platform-core PlatformAppServices | 26 |
| platform-core PlatformBootstrap | 23 |
| platform-core WebServer | 21 |
| hello-app | 10 |
| **總計** | **263** |

## 待辦任務
無 — 所有核心功能已完成，263 測試全綠，Ralph Loop 排查與修復完成。

## 未來可能的擴展（非本次範圍）
- [ ] 本地 Paper 伺服器煙霧測試（手動）
- [ ] App 可存取 Bukkit listener 代註冊（question.md A-5 提及，目前 App 可自由 import Bukkit 類）
- [ ] Web 控制台前端建構自動化在 CI 環境的處理（目前 buildWebConsole 依賴 npm）
- [ ] 更多示範 App
