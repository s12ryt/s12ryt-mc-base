package dev.s12ryt.mcapp.core.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DefaultServerAdapter 測試：無伺服器環境的後備實作。
 *
 * <p>契約：
 * <ul>
 *   <li>implements {@link ServerAdapter}</li>
 *   <li>runOnMainThread 直接在當前線程同步執行（無伺服器可跳回）</li>
 *   <li>null task 拋 NullPointerException</li>
 *   <li>多次執行順序保持（線程內序列語意）</li>
 * </ul>
 */
@DisplayName("DefaultServerAdapter：無伺服器環境直接執行")
class DefaultServerAdapterTest {

    @Test
    @DisplayName("implements ServerAdapter 介面")
    void implementsServerAdapter() {
        DefaultServerAdapter adapter = new DefaultServerAdapter();
        assertSame(ServerAdapter.class, ServerAdapter.class);
        assertTrue(adapter instanceof ServerAdapter, "DefaultServerAdapter 應實作 ServerAdapter");
    }

    @Test
    @DisplayName("runOnMainThread 直接同步執行（當前線程）")
    void runOnMainThread_executesSynchronouslyOnCurrentThread() {
        DefaultServerAdapter adapter = new DefaultServerAdapter();
        AtomicReference<Thread> executedOn = new AtomicReference<>();
        adapter.runOnMainThread(() -> executedOn.set(Thread.currentThread()));
        assertEquals(Thread.currentThread(), executedOn.get(), "應在呼叫者的當前線程執行");
    }

    @Test
    @DisplayName("多個任務按提交順序執行")
    void runOnMainThread_multipleTasks_executeInOrder() {
        DefaultServerAdapter adapter = new DefaultServerAdapter();
        List<Integer> order = new CopyOnWriteArrayList<>();
        adapter.runOnMainThread(() -> order.add(1));
        adapter.runOnMainThread(() -> order.add(2));
        adapter.runOnMainThread(() -> order.add(3));
        assertEquals(List.of(1, 2, 3), order, "同步執行應保持提交順序");
    }

    @Test
    @DisplayName("null task 拋 NullPointerException")
    void runOnMainThread_nullTask_throwsNPE() {
        DefaultServerAdapter adapter = new DefaultServerAdapter();
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> adapter.runOnMainThread(null));
    }

    @Test
    @DisplayName("無狀態：重複實例行為一致")
    void stateless_repeatedInstancesBehaveIdentically() {
        DefaultServerAdapter a1 = new DefaultServerAdapter();
        DefaultServerAdapter a2 = new DefaultServerAdapter();
        List<String> results = new CopyOnWriteArrayList<>();
        a1.runOnMainThread(() -> results.add("a1"));
        a2.runOnMainThread(() -> results.add("a2"));
        assertEquals(List.of("a1", "a2"), results);
    }
}
