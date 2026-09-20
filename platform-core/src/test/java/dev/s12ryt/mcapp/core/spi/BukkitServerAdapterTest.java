package dev.s12ryt.mcapp.core.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.bukkit.plugin.Plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BukkitServerAdapter 測試。
 *
 * <p>測試環境（無 Bukkit server）：Bukkit.getServer() 回 null，
 * 因此 runOnMainThread 的所有路徑都會 fallback 到直接同步執行。
 *
 * <p>契約：
 * <ul>
 *   <li>implements {@link ServerAdapter}</li>
 *   <li>建構子接收 {@code Supplier<Plugin>}（null supplier 拋 NPE）</li>
 *   <li>無 server 時 runOnMainThread 直接在當前線程執行</li>
 *   <li>null task 拋 NullPointerException</li>
 *   <li>plugin supplier 回 null 時（fallback 路徑）直接執行不拋例外</li>
 * </ul>
 */
@DisplayName("BukkitServerAdapter：Bukkit 主線程跳回（無 server 時 fallback 同步執行）")
class BukkitServerAdapterTest {

    @Test
    @DisplayName("implements ServerAdapter 介面")
    void implementsServerAdapter() {
        BukkitServerAdapter adapter = new BukkitServerAdapter(() -> null);
        assertTrue(adapter instanceof ServerAdapter, "BukkitServerAdapter 應實作 ServerAdapter");
    }

    @Test
    @DisplayName("null supplier 拋 NullPointerException")
    void nullSupplier_throwsNPE() {
        assertThrows(NullPointerException.class, () -> new BukkitServerAdapter(null));
    }

    @Test
    @DisplayName("無 server 時 runOnMainThread 直接同步執行（當前線程）")
    void noServer_runOnMainThread_executesSynchronously() {
        // 測試環境 Bukkit.getServer() == null
        BukkitServerAdapter adapter = new BukkitServerAdapter(() -> null);
        AtomicReference<Thread> executedOn = new AtomicReference<>();
        adapter.runOnMainThread(() -> executedOn.set(Thread.currentThread()));
        assertEquals(Thread.currentThread(), executedOn.get(), "無 server 應在當前線程同步執行");
    }

    @Test
    @DisplayName("plugin supplier 回 null 時仍正常執行（fallback 路徑）")
    void pluginSupplierReturnsNull_stillExecutes() {
        BukkitServerAdapter adapter = new BukkitServerAdapter(() -> null);
        List<String> results = new CopyOnWriteArrayList<>();
        adapter.runOnMainThread(() -> results.add("ran"));
        assertEquals(List.of("ran"), results, "plugin 為 null 時不應拋例外，應直接執行");
    }

    @Test
    @DisplayName("null task 拋 NullPointerException")
    void nullTask_throwsNPE() {
        BukkitServerAdapter adapter = new BukkitServerAdapter(() -> null);
        assertThrows(NullPointerException.class, () -> adapter.runOnMainThread(null));
    }

    @Test
    @DisplayName("supplier 被延遲呼叫（僅在需要時）")
    void supplierIsLazy_calledOnlyWhenNeeded() {
        // 無 server 環境下不會用到 plugin，supplier 不應被呼叫
        AtomicReference<Boolean> supplierCalled = new AtomicReference<>(false);
        Supplier<Plugin> lazySupplier = () -> {
            supplierCalled.set(true);
            return null;
        };
        BukkitServerAdapter adapter = new BukkitServerAdapter(lazySupplier);
        adapter.runOnMainThread(() -> {
        });
        assertNull(supplierCalled.get().booleanValue() ? "called" : null,
                "無 server 時 supplier 不應被呼叫");
    }
}
