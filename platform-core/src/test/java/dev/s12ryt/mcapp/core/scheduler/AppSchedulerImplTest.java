package dev.s12ryt.mcapp.core.scheduler;

import dev.s12ryt.mcapp.api.AppScheduler;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AppSchedulerImpl 單元測試。
 *
 * <p>因排程器涉及非同步執行緒，測試使用 CountDownLatch/AtomicInteger 等同步原語，
 * 並設定合理 timeout 避免死結。
 *
 * <p>runOnMainThread 在無 Bukkit 環境時應直接同步執行（測試環境即此情境）。
 */
class AppSchedulerImplTest {

    private AppSchedulerImpl scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AppSchedulerImpl("test-app");
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
    }

    // ─── Construction & Identity ───────────────────────────────

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("implements AppScheduler")
        void implementsAppScheduler() {
            assertInstanceOf(AppScheduler.class, scheduler);
        }

        @Test
        @DisplayName("implements AutoCloseable")
        void implementsAutoCloseable() {
            assertInstanceOf(AutoCloseable.class, scheduler);
        }

        @Test
        @DisplayName("null appId throws NPE")
        void nullAppIdThrows() {
            assertThrows(NullPointerException.class, () -> new AppSchedulerImpl(null));
        }
    }

    // ─── runAsync ──────────────────────────────────────────────

    @Nested
    @DisplayName("runAsync")
    class RunAsync {

        @Test
        @DisplayName("executes task asynchronously")
        void executesTask() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> threadName = new AtomicReference<>();

            scheduler.runAsync(() -> {
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
            });

            assertTrue(latch.await(2, TimeUnit.SECONDS), "task should execute within 2s");
            assertNotNull(threadName.get());
            assertNotEquals(Thread.currentThread().getName(), threadName.get(),
                    "task should run on a different thread");
        }

        @Test
        @DisplayName("null task throws NPE")
        void nullTaskThrows() {
            assertThrows(NullPointerException.class, () -> scheduler.runAsync(null));
        }

        @Test
        @DisplayName("multiple runAsync tasks run concurrently")
        void multipleTasksConcurrently() throws Exception {
            int count = 10;
            CountDownLatch latch = new CountDownLatch(count);
            AtomicInteger executed = new AtomicInteger(0);

            for (int i = 0; i < count; i++) {
                scheduler.runAsync(() -> {
                    executed.incrementAndGet();
                    latch.countDown();
                });
            }

            assertTrue(latch.await(3, TimeUnit.SECONDS), "all tasks should complete within 3s");
            assertEquals(count, executed.get());
        }
    }

    // ─── runOnMainThread ──────────────────────────────────────

    @Nested
    @DisplayName("runOnMainThread")
    class RunOnMainThread {

        @Test
        @DisplayName("executes synchronously when no Bukkit server")
        void executesSynchronouslyWithoutBukkit() {
            AtomicReference<String> threadName = new AtomicReference<>();
            AtomicBoolean executed = new AtomicBoolean(false);

            scheduler.runOnMainThread(() -> {
                threadName.set(Thread.currentThread().getName());
                executed.set(true);
            });

            assertTrue(executed.get());
            assertEquals(Thread.currentThread().getName(), threadName.get(),
                    "without Bukkit, task should run on caller thread");
        }

        @Test
        @DisplayName("null task throws NPE")
        void nullTaskThrows() {
            assertThrows(NullPointerException.class, () -> scheduler.runOnMainThread(null));
        }
    }

    // ─── scheduleOnce ─────────────────────────────────────────

    @Nested
    @DisplayName("scheduleOnce")
    class ScheduleOnce {

        @Test
        @DisplayName("executes after delay")
        void executesAfterDelay() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            long start = System.currentTimeMillis();

            scheduler.scheduleOnce(100, latch::countDown);

            assertTrue(latch.await(2, TimeUnit.SECONDS), "task should execute within 2s");
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed >= 80, "should wait at least ~100ms (got " + elapsed + "ms)");
        }

        @Test
        @DisplayName("returns positive task id")
        void returnsPositiveId() {
            long id = scheduler.scheduleOnce(5000, () -> {});
            assertTrue(id > 0, "task id should be positive");
        }

        @Test
        @DisplayName("null task throws NPE")
        void nullTaskThrows() {
            assertThrows(NullPointerException.class, () -> scheduler.scheduleOnce(100, null));
        }

        @Test
        @DisplayName("negative delay throws IllegalArgumentException")
        void negativeDelayThrows() {
            assertThrows(IllegalArgumentException.class, () -> scheduler.scheduleOnce(-1, () -> {}));
        }

        @Test
        @DisplayName("cancel prevents execution")
        void cancelPreventsExecution() throws Exception {
            AtomicInteger executed = new AtomicInteger(0);
            long id = scheduler.scheduleOnce(200, () -> executed.incrementAndGet());

            scheduler.cancel(id);

            Thread.sleep(500); // wait past the scheduled time
            assertEquals(0, executed.get(), "cancelled task should not execute");
        }

        @Test
        @DisplayName("cancel unknown taskId is no-op")
        void cancelUnknownIsNoOp() {
            assertDoesNotThrow(() -> scheduler.cancel(99999L));
        }

        @Test
        @DisplayName("cancel after task completed is no-op")
        void cancelAfterCompletedIsNoOp() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            long id = scheduler.scheduleOnce(50, latch::countDown);

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            Thread.sleep(100); // ensure fully completed
            assertDoesNotThrow(() -> scheduler.cancel(id));
        }
    }

    // ─── scheduleAtFixedDelay ────────────────────────────────

    @Nested
    @DisplayName("scheduleAtFixedDelay")
    class ScheduleAtFixedDelay {

        @Test
        @DisplayName("executes repeatedly at fixed delay")
        void executesRepeatedly() throws Exception {
            int target = 3;
            CountDownLatch latch = new CountDownLatch(target);
            AtomicInteger count = new AtomicInteger(0);

            scheduler.scheduleAtFixedDelay(50, 50, () -> {
                count.incrementAndGet();
                latch.countDown();
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS),
                    "should execute " + target + " times within 5s");
            assertTrue(count.get() >= target);
        }

        @Test
        @DisplayName("returns positive task id")
        void returnsPositiveId() {
            long id = scheduler.scheduleAtFixedDelay(5000, 5000, () -> {});
            assertTrue(id > 0, "task id should be positive");
        }

        @Test
        @DisplayName("null task throws NPE")
        void nullTaskThrows() {
            assertThrows(NullPointerException.class,
                    () -> scheduler.scheduleAtFixedDelay(100, 100, null));
        }

        @Test
        @DisplayName("negative initialDelay throws IllegalArgumentException")
        void negativeInitialDelayThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> scheduler.scheduleAtFixedDelay(-1, 100, () -> {}));
        }

        @Test
        @DisplayName("non-positive delay throws IllegalArgumentException")
        void nonPositiveDelayThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> scheduler.scheduleAtFixedDelay(100, 0, () -> {}));
            assertThrows(IllegalArgumentException.class,
                    () -> scheduler.scheduleAtFixedDelay(100, -1, () -> {}));
        }

        @Test
        @DisplayName("cancel stops further executions")
        void cancelStopsExecutions() throws Exception {
            AtomicInteger count = new AtomicInteger(0);
            long id = scheduler.scheduleAtFixedDelay(50, 50, () -> count.incrementAndGet());

            // let it run a few times
            Thread.sleep(200);
            int beforeCancel = count.get();
            assertTrue(beforeCancel > 0, "should have executed at least once");

            scheduler.cancel(id);
            Thread.sleep(300);
            int afterCancel = count.get();
            // After cancel, count should not increase significantly
            assertTrue(afterCancel - beforeCancel <= 1,
                    "count should not increase much after cancel (before=" + beforeCancel + ", after=" + afterCancel + ")");
        }
    }

    // ─── Close Behavior ───────────────────────────────────────

    @Nested
    @DisplayName("Close")
    class CloseBehavior {

        @Test
        @DisplayName("close is idempotent")
        void closeIsIdempotent() {
            assertDoesNotThrow(() -> scheduler.close());
            assertDoesNotThrow(() -> scheduler.close());
        }

        @Test
        @DisplayName("close shuts down executor — runAsync throws or silently fails")
        void closeShutsDownExecutor() {
            scheduler.close();
            // After close, runAsync should not execute the task (or throw RejectedExecutionException)
            // We just verify no uncaught exception propagates
            AtomicBoolean executed = new AtomicBoolean(false);
            assertDoesNotThrow(() -> {
                try {
                    scheduler.runAsync(() -> executed.set(true));
                } catch (Exception e) {
                    // RejectedExecutionException is acceptable
                }
            });
            // Give it a moment; task should NOT have executed
            try { Thread.sleep(100); } catch (InterruptedException e) { }
            assertFalse(executed.get(), "task should not execute after close");
        }

        @Test
        @DisplayName("close cancels scheduled tasks")
        void closeCancelsScheduledTasks() throws Exception {
            AtomicInteger count = new AtomicInteger(0);
            scheduler.scheduleAtFixedDelay(50, 50, () -> count.incrementAndGet());

            Thread.sleep(150);
            int before = count.get();
            assertTrue(before > 0, "should have executed at least once");

            scheduler.close();
            Thread.sleep(300);
            int after = count.get();
            assertTrue(after - before <= 1,
                    "count should not increase after close (before=" + before + ", after=" + after + ")");
        }

        @Test
        @DisplayName("close after close — scheduleOnce does not execute")
        void closePreventsScheduleOnce() throws Exception {
            scheduler.close();
            AtomicInteger executed = new AtomicInteger(0);

            try {
                scheduler.scheduleOnce(50, () -> executed.incrementAndGet());
            } catch (Exception e) {
                // RejectedExecutionException acceptable
            }

            Thread.sleep(200);
            assertEquals(0, executed.get(), "task should not execute after close");
        }
    }

    // ─── Task Id Uniqueness ───────────────────────────────────

    @Nested
    @DisplayName("Task Id")
    class TaskId {

        @Test
        @DisplayName("different tasks get different ids")
        void differentIds() {
            long id1 = scheduler.scheduleOnce(5000, () -> {});
            long id2 = scheduler.scheduleOnce(5000, () -> {});
            long id3 = scheduler.scheduleAtFixedDelay(5000, 5000, () -> {});

            assertNotEquals(id1, id2);
            assertNotEquals(id1, id3);
            assertNotEquals(id2, id3);
        }

        @Test
        @DisplayName("cancelled id can be reused conceptually (no collision with new tasks)")
        void cancelledIdDoesNotCollide() {
            long id1 = scheduler.scheduleOnce(5000, () -> {});
            scheduler.cancel(id1);
            long id2 = scheduler.scheduleOnce(5000, () -> {});

            // ids are monotonic, so id2 > id1 — they are different
            assertNotEquals(id1, id2);
        }
    }
}
