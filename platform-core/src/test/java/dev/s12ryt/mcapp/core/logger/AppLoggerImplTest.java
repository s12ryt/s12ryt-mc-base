package dev.s12ryt.mcapp.core.logger;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.s12ryt.mcapp.api.AppLogger;

class AppLoggerImplTest {

    @TempDir
    Path tmp;

    private Path logDir;
    private String appId;

    @BeforeEach
    void setUp() {
        logDir = tmp.resolve("logs");
        appId = "test-app";
    }

    // =========================================================================
    // Construction
    // =========================================================================

    @Nested
    class Construction {

        @Test
        void createsLogDirectoryIfAbsent() throws IOException {
            assertFalse(Files.exists(logDir));
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            logger.close();
            assertTrue(Files.isDirectory(logDir));
        }

        @Test
        void existingDirectoryDoesNotThrow() throws IOException {
            Files.createDirectories(logDir);
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            logger.close();
        }

        @Test
        void nullAppIdThrowsNpe() {
            assertThrows(NullPointerException.class, () -> new AppLoggerImpl(null, logDir));
        }

        @Test
        void nullLogDirThrowsNpe() {
            assertThrows(NullPointerException.class, () -> new AppLoggerImpl(appId, null));
        }

        @Test
        void implementsAutoCloseable() {
            assertTrue(java.lang.AutoCloseable.class.isAssignableFrom(AppLoggerImpl.class));
        }
    }

    // =========================================================================
    // Logging
    // =========================================================================

    @Nested
    class Logging {

        @Test
        void infoWritesToRingBuffer() {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            logger.info("hello world");
            List<String> lines = logger.recentLines(100);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("INFO"));
            assertTrue(lines.get(0).contains("hello world"));
            logger.close();
        }

        @Test
        void warnWritesToRingBuffer() {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            logger.warn("careful");
            List<String> lines = logger.recentLines(100);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("WARN"));
            assertTrue(lines.get(0).contains("careful"));
            logger.close();
        }

        @Test
        void errorWritesToRingBuffer() {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            logger.error("oops", new RuntimeException("boom"));
            List<String> lines = logger.recentLines(100);
            // error line + at least one stack trace line
            assertTrue(lines.size() >= 1);
            assertTrue(lines.get(0).contains("ERROR"));
            assertTrue(lines.get(0).contains("oops"));
            logger.close();
        }

        @Test
        void errorWithNullThrowableStillLogs() {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            logger.error("fail", null);
            List<String> lines = logger.recentLines(100);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("ERROR"));
            assertTrue(lines.get(0).contains("fail"));
            logger.close();
        }

        @Test
        void multipleLinesOrderedOldestFirst() {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            logger.info("line-1");
            logger.info("line-2");
            logger.warn("line-3");
            List<String> lines = logger.recentLines(100);
            assertEquals(3, lines.size());
            assertTrue(lines.get(0).contains("line-1"));
            assertTrue(lines.get(1).contains("line-2"));
            assertTrue(lines.get(2).contains("line-3"));
            logger.close();
        }

        @Test
        void recentLinesZeroOrNegativeReturnsDefault() {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            logger.info("a");
            List<String> lines = logger.recentLines(0);
            assertFalse(lines.isEmpty());
            assertTrue(lines.get(0).contains("a"));
            logger.close();
        }

        @Test
        void recentLinesLimitedByMaxLines() {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            for (int i = 0; i < 50; i++) {
                logger.info("msg-" + i);
            }
            List<String> lines = logger.recentLines(10);
            assertEquals(10, lines.size());
            // should be the last 10 entries
            assertTrue(lines.get(0).contains("msg-40"));
            assertTrue(lines.get(9).contains("msg-49"));
            logger.close();
        }
    }

    // =========================================================================
    // Ring Buffer Overflow
    // =========================================================================

    @Nested
    class RingBufferOverflow {

        @Test
        void ringBufferDropsOldEntriesWhenCapacityExceeded() {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            // capacity should be at least 500, write 600 to overflow
            for (int i = 0; i < 600; i++) {
                logger.info("overflow-" + i);
            }
            List<String> lines = logger.recentLines(1000);
            // ring buffer should have capped; entries < 600
            assertTrue(lines.size() < 600, "ring buffer should cap entries, got " + lines.size());
            assertTrue(lines.size() >= 200, "ring buffer should hold at least 200, got " + lines.size());
            // latest entry should still be present
            assertTrue(lines.get(lines.size() - 1).contains("overflow-599"));
            logger.close();
        }

        @Test
        void ringBufferKeepsLatestEntriesAfterOverflow() {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            for (int i = 0; i < 600; i++) {
                logger.info("entry-" + i);
            }
            List<String> lines = logger.recentLines(3);
            assertEquals(3, lines.size());
            assertTrue(lines.get(0).contains("entry-597"));
            assertTrue(lines.get(1).contains("entry-598"));
            assertTrue(lines.get(2).contains("entry-599"));
            logger.close();
        }
    }

    // =========================================================================
    // File Persistence
    // =========================================================================

    @Nested
    class FilePersistence {

        @Test
        void logsWrittenToFile() throws IOException {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            logger.info("file-test");
            logger.warn("file-warn");
            logger.close();

            Path logFile = logDir.resolve(appId + ".log");
            assertTrue(Files.exists(logFile), "log file should exist");
            String content = Files.readString(logFile);
            assertTrue(content.contains("file-test"));
            assertTrue(content.contains("file-warn"));
        }

        @Test
        void errorStackTraceWrittenToFile() throws IOException {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            logger.error("crash", new RuntimeException("root-cause"));
            logger.close();

            Path logFile = logDir.resolve(appId + ".log");
            String content = Files.readString(logFile);
            assertTrue(content.contains("crash"));
            assertTrue(content.contains("RuntimeException"));
            assertTrue(content.contains("root-cause"));
        }

        @Test
        void appendsToExistingLogFile() throws IOException {
            // first logger instance writes
            AppLoggerImpl logger1 = new AppLoggerImpl(appId, logDir);
            logger1.info("first-session");
            logger1.close();

            // second instance should append
            AppLoggerImpl logger2 = new AppLoggerImpl(appId, logDir);
            logger2.info("second-session");
            logger2.close();

            Path logFile = logDir.resolve(appId + ".log");
            String content = Files.readString(logFile);
            assertTrue(content.contains("first-session"));
            assertTrue(content.contains("second-session"));
        }
    }

    // =========================================================================
    // Close Behavior
    // =========================================================================

    @Nested
    class CloseBehavior {

        @Test
        void closeIsIdempotent() {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            logger.info("before-close");
            logger.close();
            logger.close(); // should not throw
        }

        @Test
        void closeStopsNewWrites() {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            logger.info("before-close");
            logger.close();
            logger.info("after-close");
            // recentLines should not contain "after-close"
            List<String> lines = logger.recentLines(100);
            assertTrue(lines.stream().noneMatch(l -> l.contains("after-close")));
            assertTrue(lines.stream().anyMatch(l -> l.contains("before-close")));
        }

        @Test
        void recentLinesStillReadableAfterClose() {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            logger.info("persisted");
            logger.close();
            List<String> lines = logger.recentLines(100);
            assertFalse(lines.isEmpty());
            assertTrue(lines.stream().anyMatch(l -> l.contains("persisted")));
        }

        @Test
        void closeFlushesFileBuffer() throws IOException {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            logger.info("flush-test");
            logger.close();

            Path logFile = logDir.resolve(appId + ".log");
            // file should contain the data immediately after close
            String content = Files.readString(logFile);
            assertTrue(content.contains("flush-test"));
        }
    }

    // =========================================================================
    // Thread Safety
    // =========================================================================

    @Nested
    class ThreadSafety {

        @Test
        void concurrentWritesAreSafe() throws Exception {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            // keep total writes under ring buffer capacity (500) so no entries are dropped
            int threadCount = 8;
            int perThread = 50; // 8*50=400 < 500
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger errors = new AtomicInteger();

            for (int t = 0; t < threadCount; t++) {
                final int tid = t;
                pool.submit(() -> {
                    try {
                        latch.await();
                        for (int i = 0; i < perThread; i++) {
                            logger.info("t" + tid + "-msg" + i);
                        }
                    } catch (Throwable e) {
                        errors.incrementAndGet();
                    }
                });
            }

            latch.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
            assertEquals(0, errors.get(), "no threads should throw");

            List<String> lines = logger.recentLines(threadCount * perThread);
            // all 400 messages should be present (within ring buffer capacity)
            assertEquals(threadCount * perThread, lines.size(), "all concurrent writes should be in ring buffer");
            logger.close();
        }
    }

    // =========================================================================
    // Null Message
    // =========================================================================

    @Nested
    class NullMessage {

        @Test
        void infoNullMessageDoesNotThrow() {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            assertDoesNotThrow(() -> logger.info(null));
            List<String> lines = logger.recentLines(100);
            assertEquals(1, lines.size());
            logger.close();
        }

        @Test
        void errorNullMessageWithThrowableDoesNotThrow() {
            AppLoggerImpl logger = new AppLoggerImpl(appId, logDir);
            assertDoesNotThrow(() -> logger.error(null, new RuntimeException("e")));
            List<String> lines = logger.recentLines(100);
            assertFalse(lines.isEmpty());
            logger.close();
        }
    }
}
