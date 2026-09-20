package dev.s12ryt.mcapp.core.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AppContainer TDD 測試：18 個行為驗證。
 *
 * <p>涵蓋：loadAll / load / unload / reload / get / list / close，
 * 含正常路徑、邊界值（空目錄、非 jar、子目錄）、錯誤路徑
 * （缺 properties、無效 id、main 不存在、main 非 App、onEnable 拋例外、
 * onDisable 拋例外、createContext 失敗）與資源清理（jar 檔案鎖釋放）。
 *
 * <p>每個載入 App 的測試均以 try-with-resources 確保 container.close()，
 * 使 URLClassLoader 釋放 jar 檔案鎖，@TempDir 才能清理。
 */
class AppContainerTest {

    @TempDir
    Path tmp;

    private Path appsDir;
    private FakeServices services;
    private ClassLoader shared;

    // fixture 類別全名
    private static final String RECORDING = "dev.s12ryt.mcapp.core.app.testapps.RecordingApp";
    private static final String FAILING = "dev.s12ryt.mcapp.core.app.testapps.FailingApp";
    private static final String BAD_DISABLE = "dev.s12ryt.mcapp.core.app.testapps.BadDisableApp";
    private static final String NOT_AN_APP = "dev.s12ryt.mcapp.core.app.testapps.NotAnApp";

    @BeforeEach
    void setUp() {
        appsDir = tmp.resolve("apps");
        services = new FakeServices(tmp.resolve("data"));
        shared = AppContainerTest.class.getClassLoader();
    }

    // ─── helper ───

    private AppContainer newContainer() {
        return new AppContainer(appsDir, services, shared);
    }

    private String props(String id, String name, String version, String main) {
        return "app.id=" + id + "\n"
                + "app.name=" + name + "\n"
                + "app.version=" + version + "\n"
                + "app.main=" + main + "\n";
    }

    private Path appJar(String fileName, String properties, String... classNames) throws IOException {
        Files.createDirectories(appsDir);
        return writeJar(appsDir.resolve(fileName), properties, classNames);
    }

    private Path appJarInDir(Path dir, String fileName, String properties, String... classNames) throws IOException {
        Files.createDirectories(dir);
        return writeJar(dir.resolve(fileName), properties, classNames);
    }

    private Path writeJar(Path jar, String properties, String... classNames) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
            if (properties != null) {
                jos.putNextEntry(new JarEntry("app.properties"));
                jos.write(properties.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                jos.closeEntry();
            }
            Path classesDir = Path.of(System.getProperty(
                    "testAppsClassesDir", "build/classes/java/testApps"));
            for (String cn : classNames) {
                String entryPath = cn.replace('.', '/') + ".class";
                Path classFile = classesDir.resolve(entryPath);
                assertTrue(Files.exists(classFile),
                        "fixture class not found: " + classFile);
                jos.putNextEntry(new JarEntry(entryPath));
                Files.copy(classFile, jos);
                jos.closeEntry();
            }
        }
        return jar;
    }

    // ─── 1. loadAll：正常 + 壞 jar ───

    @Test
    void loadAll_startsValidApps_andReportsBrokenJars() throws Exception {
        appJar("good.jar", props("good-app", "Good App", "1.0.0", RECORDING), RECORDING);
        appJar("bad.jar", null);

        try (AppContainer container = newContainer()) {
            List<AppContainer.AppHandle> handles = container.loadAll();

            assertEquals(2, handles.size());
            AppContainer.AppHandle good = handles.stream()
                    .filter(h -> h.state() == AppContainer.AppState.RUNNING).findFirst().orElseThrow();
            assertEquals("good-app", good.id());

            AppContainer.AppHandle bad = handles.stream()
                    .filter(h -> h.state() == AppContainer.AppState.FAILED).findFirst().orElseThrow();
            assertNotNull(bad.error());
        }
    }

    // ─── 2. loadAll：忽略非 jar 與子目錄 ───

    @Test
    void loadAll_ignoresNonJarFiles_andSubdirectories() throws Exception {
        appJar("good.jar", props("good-app", "Good", "1.0.0", RECORDING), RECORDING);
        Files.writeString(appsDir.resolve("notes.txt"), "hello");
        Path subdir = Files.createDirectory(appsDir.resolve("subdir"));
        appJarInDir(subdir, "inner.jar", props("inner", "Inner", "1.0.0", RECORDING), RECORDING);

        try (AppContainer container = newContainer()) {
            List<AppContainer.AppHandle> handles = container.loadAll();

            assertEquals(1, handles.size());
            assertEquals(1, container.list().size());
        }
    }

    // ─── 3. loadAll：目錄不存在時自動建立 ───

    @Test
    void loadAll_missingDirectory_createsItAndReturnsEmpty() {
        assertFalse(Files.exists(appsDir));

        List<AppContainer.AppHandle> handles = newContainer().loadAll();

        assertTrue(handles.isEmpty());
        assertTrue(Files.isDirectory(appsDir));
    }

    // ─── 4. load：啟動 App，驗證 context 接線 ───

    @Test
    void load_startsApp_withWiredContext() throws Exception {
        Path jar = appJar("good.jar", props("good-app", "Good App", "1.2.3", RECORDING), RECORDING);

        try (AppContainer container = newContainer()) {
            AppContainer.AppHandle handle = container.load(jar);

            assertEquals(AppContainer.AppState.RUNNING, handle.state());
            assertEquals("good-app", handle.id());
            assertEquals("Good App", handle.name());
            assertEquals("1.2.3", handle.version());

            Path startedTxt = tmp.resolve("data").resolve("good-app").resolve("data").resolve("started.txt");
            assertTrue(Files.exists(startedTxt), "started.txt should exist");
            assertEquals("good-app", Files.readString(startedTxt).trim());

            List<String> log = services.lastCapture();
            assertTrue(log.stream().anyMatch(l -> l.contains("INFO enabled#1 server=null")),
                    "log should contain 'INFO enabled#1 server=null', got: " + log);

            assertEquals(1, services.createdSpecs().size());
            assertEquals(0, services.destroyedSpecs().size());
        }
    }

    // ─── 5. load：同 jar 重複載入第二次失敗 ───

    @Test
    void load_sameJarTwice_secondFailsWithoutDisturbingFirst() throws Exception {
        Path jar = appJar("good.jar", props("good-app", "Good", "1.0.0", RECORDING), RECORDING);

        try (AppContainer container = newContainer()) {
            AppContainer.AppHandle first = container.load(jar);
            assertEquals(AppContainer.AppState.RUNNING, first.state());

            AppContainer.AppHandle second = container.load(jar);
            assertEquals(AppContainer.AppState.FAILED, second.state());
            assertNotNull(second.error());

            assertEquals(1, container.list().size());
            assertEquals(AppContainer.AppState.RUNNING, container.list().get(0).state());
            assertEquals(1, services.contexts().size());
        }
    }

    // ─── 6. loadAll：重複 id ───

    @Test
    void loadAll_duplicateIds_secondJarFails() throws Exception {
        appJar("a-dup.jar", props("dup-app", "A", "1.0.0", RECORDING), RECORDING);
        appJar("b-dup.jar", props("dup-app", "B", "1.0.0", RECORDING), RECORDING);

        try (AppContainer container = newContainer()) {
            List<AppContainer.AppHandle> handles = container.loadAll();

            assertEquals(2, handles.size());
            long running = handles.stream().filter(h -> h.state() == AppContainer.AppState.RUNNING).count();
            long failed = handles.stream().filter(h -> h.state() == AppContainer.AppState.FAILED).count();
            assertEquals(1, running);
            assertEquals(1, failed);
            assertEquals(1, container.list().size());
        }
    }

    // ─── 7. reload：新 ClassLoader 重置 static ───

    @Test
    void reload_loadsFreshClassState_andKeepsSingleEntry() throws Exception {
        Path jar = appJar("good.jar", props("good-app", "Good", "1.0.0", RECORDING), RECORDING);

        try (AppContainer container = newContainer()) {
            container.load(jar);
            FakeAppContext firstCtx = services.contexts().get(0);

            Optional<AppContainer.AppHandle> reloaded = container.reload("good-app");

            assertTrue(reloaded.isPresent());
            assertEquals(AppContainer.AppState.RUNNING, reloaded.get().state());
            assertEquals(1, container.list().size());

            List<String> firstLog = firstCtx.logLines();
            assertTrue(firstLog.stream().anyMatch(l -> l.contains("disabled#1")),
                    "first capture should contain 'disabled#1', got: " + firstLog);

            List<String> secondLog = services.lastCapture();
            assertTrue(secondLog.stream().anyMatch(l -> l.contains("enabled#1")),
                    "second capture should contain 'enabled#1' (fresh classloader), got: " + secondLog);
        }
    }

    // ─── 8. unload：停止 + 清理 + 移除 ───

    @Test
    void unload_stopsApp_releasesResources_andRemovesEntry() throws Exception {
        Path jar = appJar("good.jar", props("good-app", "Good", "1.0.0", RECORDING), RECORDING);

        try (AppContainer container = newContainer()) {
            container.load(jar);

            boolean unloaded = container.unload("good-app");

            assertTrue(unloaded);
            assertTrue(container.get("good-app").isEmpty());
            assertTrue(container.list().isEmpty());
            assertEquals(1, services.destroyedSpecs().size());

            List<String> log = services.lastCapture();
            assertTrue(log.stream().anyMatch(l -> l.contains("disabled#1")),
                    "log should contain 'disabled#1', got: " + log);

            assertFalse(container.unload("good-app"));
        }
    }

    // ─── 9. unload：釋放 jar 檔案鎖 ───

    @Test
    void unload_releasesJarFileLock() throws Exception {
        Path jar = appJar("good.jar", props("good-app", "Good", "1.0.0", RECORDING), RECORDING);

        try (AppContainer container = newContainer()) {
            container.load(jar);
            container.unload("good-app");

            assertDoesNotThrow(() -> Files.delete(jar));
        }
    }

    // ─── 10. unload：onDisable 拋例外仍清理 ───

    @Test
    void unload_onDisableThrowing_stillCleansUp() throws Exception {
        Path jar = appJar("bad.jar", props("bad-disable", "Bad", "1.0.0", BAD_DISABLE), BAD_DISABLE);

        try (AppContainer container = newContainer()) {
            container.load(jar);

            boolean unloaded = container.unload("bad-disable");

            assertTrue(unloaded);
            assertTrue(container.list().isEmpty());
            assertEquals(1, services.destroyedSpecs().size());

            List<String> log = services.lastCapture();
            assertTrue(log.stream().anyMatch(l -> l.contains("bad-disable-onEnable")),
                    "log should contain 'bad-disable-onEnable', got: " + log);
        }
    }

    // ─── 11. 啟用失敗：標記 FAILED + 清理 + 保留條目 ───

    @Test
    void enableFailure_marksFailed_cleansAndKeepsEntry() throws Exception {
        Path jar = appJar("fail.jar", props("fail-app", "Fail", "1.0.0", FAILING), FAILING);

        try (AppContainer container = newContainer()) {
            AppContainer.AppHandle handle = container.load(jar);

            assertEquals(AppContainer.AppState.FAILED, handle.state());
            assertNotNull(handle.error());
            assertTrue(handle.error().contains("boom"),
                    "error should contain 'boom', got: " + handle.error());

            assertEquals(1, container.list().size());
            assertEquals(AppContainer.AppState.FAILED, container.list().get(0).state());

            assertEquals(1, services.createdSpecs().size());
            assertEquals(1, services.destroyedSpecs().size());

            List<String> log = services.lastCapture();
            assertFalse(log.stream().anyMatch(l -> l.contains("failing-disabled")),
                    "onDisable should not be called on enable failure, got: " + log);
        }
    }

    // ─── 12. unload FAILED 條目：不重複 destroy ───

    @Test
    void unload_failedEntry_removesWithoutDoubleDestroy() throws Exception {
        Path jar = appJar("fail.jar", props("fail-app", "Fail", "1.0.0", FAILING), FAILING);

        try (AppContainer container = newContainer()) {
            container.load(jar);
            assertEquals(1, services.destroyedSpecs().size());

            boolean unloaded = container.unload("fail-app");

            assertTrue(unloaded);
            assertTrue(container.list().isEmpty());
            assertEquals(1, services.destroyedSpecs().size());
        }
    }

    // ─── 13. 缺 app.properties ───

    @Test
    void badJar_missingProperties() throws Exception {
        Path jar = appJar("no-props.jar", null);

        try (AppContainer container = newContainer()) {
            AppContainer.AppHandle handle = container.load(jar);

            assertEquals(AppContainer.AppState.FAILED, handle.state());
            assertNotNull(handle.error());
            assertTrue(handle.error().contains("app.properties"),
                    "error should mention 'app.properties', got: " + handle.error());
        }
    }

    // ─── 14. 無效 app.id ───

    @Test
    void badJar_invalidAppId() throws Exception {
        Path jar = appJar("bad-id.jar",
                props("Bad_ID", "Bad", "1.0.0", RECORDING), RECORDING);

        try (AppContainer container = newContainer()) {
            AppContainer.AppHandle handle = container.load(jar);

            assertEquals(AppContainer.AppState.FAILED, handle.state());
            assertNotNull(handle.error());
            assertTrue(handle.error().contains("app.id"),
                    "error should mention 'app.id', got: " + handle.error());
        }
    }

    // ─── 15. main 不實作 App ───

    @Test
    void badJar_mainNotImplementingApp() throws Exception {
        Path jar = appJar("not-app.jar",
                props("not-app", "Not", "1.0.0", NOT_AN_APP), NOT_AN_APP);

        try (AppContainer container = newContainer()) {
            AppContainer.AppHandle handle = container.load(jar);

            assertEquals(AppContainer.AppState.FAILED, handle.state());
            assertNotNull(handle.error());
            assertTrue(handle.error().contains("App"),
                    "error should mention 'App', got: " + handle.error());
        }
    }

    // ─── 16. main 類別不存在 ───

    @Test
    void badJar_mainClassMissing() throws Exception {
        Path jar = appJar("missing-main.jar",
                props("missing-main", "Missing", "1.0.0", "dev.s12ryt.nope.Missing"));

        try (AppContainer container = newContainer()) {
            AppContainer.AppHandle handle = container.load(jar);

            assertEquals(AppContainer.AppState.FAILED, handle.state());
            assertNotNull(handle.error());
            assertTrue(handle.error().contains("ClassNotFoundException"),
                    "error should contain 'ClassNotFoundException', got: " + handle.error());
        }
    }

    // ─── 17. createContext 失敗 ───

    @Test
    void createContextFailure_failsWithoutEntryOrDestroy() throws Exception {
        Path jar = appJar("good.jar", props("good-app", "Good", "1.0.0", RECORDING), RECORDING);
        services.setFailCreate(true);

        try (AppContainer container = newContainer()) {
            AppContainer.AppHandle handle = container.load(jar);

            assertEquals(AppContainer.AppState.FAILED, handle.state());
            assertTrue(handle.error().contains("create-boom"),
                    "error should contain 'create-boom', got: " + handle.error());
            assertTrue(container.list().isEmpty());
            assertTrue(services.contexts().isEmpty());
            assertEquals(0, services.destroyedSpecs().size());
        }
    }

    // ─── 18. close：全部卸載 + 冪等 ───

    @Test
    void close_unloadsAllApps_isIdempotent() throws Exception {
        appJar("a.jar", props("app-a", "A", "1.0.0", RECORDING), RECORDING);
        appJar("b.jar", props("app-b", "B", "1.0.0", RECORDING), RECORDING);
        AppContainer container = newContainer();
        container.loadAll();
        assertEquals(2, container.list().size());

        container.close();

        assertTrue(container.list().isEmpty());
        assertEquals(2, services.destroyedSpecs().size());

        container.close();
        assertEquals(2, services.destroyedSpecs().size());
    }
}
