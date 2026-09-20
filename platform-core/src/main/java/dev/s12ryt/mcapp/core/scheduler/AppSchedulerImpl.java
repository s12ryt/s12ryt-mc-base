package dev.s12ryt.mcapp.core.scheduler;

import dev.s12ryt.mcapp.api.AppScheduler;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import dev.s12ryt.mcapp.core.spi.DefaultServerAdapter;
import dev.s12ryt.mcapp.core.spi.ServerAdapter;

/**
 * App 專屬排程器實作。
 *
 * <p>底層為單一 ScheduledExecutorService（守護線程），提供：
 * <ul>
 *   <li>{@link #scheduleAtFixedDelay} — 固定延遲重複執行</li>
 *   <li>{@link #scheduleOnce} — 延遲一次性執行</li>
 *   <li>{@link #runAsync} — 立即非同步</li>
 *   <li>{@link #runOnMainThread} — 跳回 Bukkit 主線程（無 Bukkit 時直接同步執行）</li>
 * </ul>
 *
 * <p>每個任務回傳唯一 id（自增），對應 ConcurrentHashMap 中的 ScheduledFuture，
 * 供 {@link #cancel} 使用。close() 時 shutdown 執行緒池，所有排程任務被取消。
 *
 * <p>非同步任務例外不會中斷執行緒池（ScheduledExecutorService 預設 swallow），
 * 但會記錄到 stderr。
 */
public final class AppSchedulerImpl implements AppScheduler, AutoCloseable {

    private final String appId;
    private final ScheduledExecutorService executor;
    private final AtomicLong idCounter = new AtomicLong(0);
    private final Map<Long, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final Supplier<ServerAdapter> serverAdapterSupplier;
    private volatile boolean closed = false;

    /**
     * 建構子（無 adapter 注入；測試用，runOnMainThread 直接同步執行）。
     *
     * @param appId App 識別碼（用於執行緒命名）
     */
    public AppSchedulerImpl(String appId) {
        this(appId, DefaultServerAdapter::new);
    }

    /**
     * 建構子（注入 ServerAdapter supplier，用於主線程跳回）。
     *
     * @param appId                App 識別碼（用於執行緒命名）
     * @param serverAdapterSupplier ServerAdapter 供應器（延遲取用）
     */
    public AppSchedulerImpl(String appId, Supplier<ServerAdapter> serverAdapterSupplier) {
        this.appId = Objects.requireNonNull(appId, "appId");
        this.serverAdapterSupplier = Objects.requireNonNull(serverAdapterSupplier, "serverAdapterSupplier");
        this.executor = Executors.newScheduledThreadPool(
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
                new AppThreadFactory(appId)
        );
    }

    @Override
    public long scheduleAtFixedDelay(long initialDelayMillis, long delayMillis, Runnable task) {
        checkInitialDelay(initialDelayMillis);
        checkDelay(delayMillis);
        java.util.Objects.requireNonNull(task, "task");
        ensureOpen();

        Runnable wrapped = wrap(task);
        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(
                wrapped,
                initialDelayMillis,
                delayMillis,
                TimeUnit.MILLISECONDS
        );
        return register(future);
    }

    @Override
    public long scheduleOnce(long delayMillis, Runnable task) {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("delayMillis must be >= 0 (got " + delayMillis + ")");
        }
        java.util.Objects.requireNonNull(task, "task");
        ensureOpen();

        Runnable wrapped = wrap(task);
        ScheduledFuture<?> future = executor.schedule(
                wrapped,
                delayMillis,
                TimeUnit.MILLISECONDS
        );
        return register(future);
    }

    @Override
    public void cancel(long taskId) {
        ScheduledFuture<?> future = tasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
    }

    @Override
    public void runOnMainThread(Runnable task) {
        java.util.Objects.requireNonNull(task, "task");
        serverAdapterSupplier.get().runOnMainThread(task);
    }

    @Override
    public void runAsync(Runnable task) {
        java.util.Objects.requireNonNull(task, "task");
        ensureOpen();
        try {
            executor.submit(wrap(task));
        } catch (RejectedExecutionException e) {
            if (!closed) {
                throw e;
            }
            // executor 已關閉，靜默拒絕
        }
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdownNow();
        tasks.clear();
    }

    // ─── Internal helpers ──────────────────────────────────

    private void ensureOpen() {
        if (closed) {
            throw new RejectedExecutionException("scheduler for app '" + appId + "' is closed");
        }
    }

    private long register(ScheduledFuture<?> future) {
        long id = idCounter.incrementAndGet();
        tasks.put(id, future);
        return id;
    }

    /** 包裝任務，捕捉未處理例外避免靜默遺失。 */
    private Runnable wrap(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                // 記錄但不傳播（ScheduledExecutorService 預設會 swallow，但顯式記錄更安全）
                System.err.println("[app:" + appId + "] scheduled task threw: " + t);
                t.printStackTrace(System.err);
            }
        };
    }

    private void checkInitialDelay(long initialDelayMillis) {
        if (initialDelayMillis < 0) {
            throw new IllegalArgumentException(
                    "initialDelayMillis must be >= 0 (got " + initialDelayMillis + ")");
        }
    }

    private void checkDelay(long delayMillis) {
        if (delayMillis <= 0) {
            throw new IllegalArgumentException(
                    "delayMillis must be > 0 (got " + delayMillis + ")");
        }
    }

    // ─── Thread Factory ─────────────────────────────────────

    private static final class AppThreadFactory implements ThreadFactory {
        private final String appId;
        private final AtomicLong count = new AtomicLong(0);

        AppThreadFactory(String appId) {
            this.appId = appId;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "app-" + appId + "-" + count.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    }
}
