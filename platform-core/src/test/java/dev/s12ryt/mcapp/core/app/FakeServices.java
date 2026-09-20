package dev.s12ryt.mcapp.core.app;

import dev.s12ryt.mcapp.api.AppContext;
import dev.s12ryt.mcapp.api.AppSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 測試用 AppServices 替身。
 *
 * <p>記錄所有 createContext / destroyContext 呼叫，供測試斷言。
 * createContext 時建立 apps/{appId}/data/ 目錄，回傳 FakeAppContext。
 * 設 failCreate=true 時 createContext 拋 RuntimeException，模擬平台建立 context 失敗。
 *
 * <p>captures 存的是 FakeAppContext 實例參照（而非 logLines 快照），
 * 如此 App 在 onEnable/onDisable 寫入的日誌可被即時讀取。
 */
public class FakeServices implements AppServices {

    private final Path dataRoot;

    private final List<AppSpec> createdSpecs = Collections.synchronizedList(new ArrayList<>());
    private final List<AppSpec> destroyedSpecs = Collections.synchronizedList(new ArrayList<>());
    private final List<FakeAppContext> contexts = Collections.synchronizedList(new ArrayList<>());

    private boolean failCreate = false;

    public FakeServices(Path dataRoot) {
        this.dataRoot = dataRoot;
    }

    public void setFailCreate(boolean fail) {
        this.failCreate = fail;
    }

    public List<AppSpec> createdSpecs() {
        return List.copyOf(createdSpecs);
    }

    public List<AppSpec> destroyedSpecs() {
        return List.copyOf(destroyedSpecs);
    }

    public List<FakeAppContext> contexts() {
        return List.copyOf(contexts);
    }

    /** 取得最後一個 context 的日誌行（即時讀取）。 */
    public List<String> lastCapture() {
        if (contexts.isEmpty()) {
            return List.of();
        }
        return contexts.get(contexts.size() - 1).logLines();
    }

    @Override
    public AppContext createContext(AppSpec spec, ClassLoader appClassLoader) {
        if (failCreate) {
            throw new RuntimeException("create-boom");
        }
        createdSpecs.add(spec);
        Path appDir = dataRoot.resolve(spec.id());
        Path dataDir = appDir.resolve("data");
        try {
            Files.createDirectories(dataDir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        FakeAppContext ctx = new FakeAppContext(spec, dataDir);
        contexts.add(ctx);
        return ctx;
    }

    @Override
    public void destroyContext(AppSpec spec) {
        destroyedSpecs.add(spec);
    }
}
