package dev.s12ryt.mcapp.core.logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import dev.s12ryt.mcapp.api.AppLogger;

/**
 * App 獨立日誌實作。
 *
 * <p>記憶體 ring buffer（上限 500 行）+ 檔案持久化（apps/{appId}.log）。
 * <p>線程安全：單一 ReentrantLock 保護 ring buffer + 檔案寫入。
 * <p>close 後不再接受新寫入，recentLines 仍可讀取。
 */
public final class AppLoggerImpl implements AppLogger, AutoCloseable {

    private static final int RING_BUFFER_CAPACITY = 500;
    private static final int DEFAULT_RECENT_LINES = 200;
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String appId;
    private final Path logFile;
    private final Deque<String> ringBuffer = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean closed = false;

    /**
     * 建構子。
     *
     * @param appId  App 識別碼（用於日誌行前綴與檔名）
     * @param logDir 日誌目錄（不存在則建立）
     */
    public AppLoggerImpl(String appId, Path logDir) {
        this.appId = Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(logDir, "logDir");
        try {
            Files.createDirectories(logDir);
        } catch (IOException e) {
            throw new IllegalStateException("無法建立日誌目錄: " + logDir, e);
        }
        this.logFile = logDir.resolve(appId + ".log");
        // touch file to ensure it exists (append mode will create it, but explicit is safer)
        if (!Files.exists(logFile)) {
            try {
                Files.createFile(logFile);
            } catch (IOException e) {
                throw new IllegalStateException("無法建立日誌檔案: " + logFile, e);
            }
        }
    }

    @Override
    public void info(String message) {
        log("INFO", message, null);
    }

    @Override
    public void warn(String message) {
        log("WARN", message, null);
    }

    @Override
    public void error(String message, Throwable t) {
        log("ERROR", message, t);
    }

    @Override
    public List<String> recentLines(int maxLines) {
        int limit = maxLines <= 0 ? DEFAULT_RECENT_LINES : maxLines;
        lock.lock();
        try {
            List<String> result = new ArrayList<>(Math.min(limit, ringBuffer.size()));
            // ArrayDeque iterator is from head (oldest) to tail (newest)
            // take the last 'limit' entries
            int skip = Math.max(0, ringBuffer.size() - limit);
            int skipped = 0;
            for (String line : ringBuffer) {
                if (skipped < skip) {
                    skipped++;
                    continue;
                }
                result.add(line);
            }
            return List.copyOf(result);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 關閉日誌器。close 後不再接受新寫入，recentLines 仍可讀取。冪等。
     */
    @Override
    public void close() {
        lock.lock();
        try {
            closed = true;
        } finally {
            lock.unlock();
        }
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private void log(String level, String message, Throwable t) {
        if (closed) {
            return;
        }
        String msg = message == null ? "" : message;
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String header = String.format("[%s] [%s] [%s] %s", timestamp, level, appId, msg);

        lock.lock();
        try {
            if (closed) {
                return;
            }
            // ring buffer
            if (ringBuffer.size() >= RING_BUFFER_CAPACITY) {
                ringBuffer.pollFirst();
            }
            ringBuffer.addLast(header);

            // stack trace lines
            if (t != null) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                String[] traceLines = sw.toString().split("\n");
                for (String traceLine : traceLines) {
                    if (traceLine.isEmpty()) continue;
                    String tl = traceLine.trim();
                    if (ringBuffer.size() >= RING_BUFFER_CAPACITY) {
                        ringBuffer.pollFirst();
                    }
                    ringBuffer.addLast(tl);
                }
            }

            // file write
            writeToFile(header, t);
        } finally {
            lock.unlock();
        }
    }

    private void writeToFile(String header, Throwable t) {
        try {
            StringBuilder sb = new StringBuilder(header);
            sb.append('\n');
            if (t != null) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                sb.append(sw);
            }
            Files.writeString(logFile, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // file write failure should not crash App; log to stderr
            System.err.println("[AppLoggerImpl] failed to write log file: " + e.getMessage());
        }
    }
}
