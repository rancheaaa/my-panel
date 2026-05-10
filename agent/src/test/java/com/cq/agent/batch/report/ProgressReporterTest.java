package com.cq.agent.batch.report;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 进度上报器单元测试
 * 覆盖率目标：100%
 */
@DisplayName("进度上报器 - ProgressReporter")
class ProgressReporterTest {

    private ProgressReporter progressReporter;

    @BeforeEach
    void setUp() {
        progressReporter = new ProgressReporter("http://localhost:9876");
    }

    // ==================== 1. 上报成功 ====================

    @Test
    @DisplayName("1. reportProgress() - 上报成功")
    void testReportProgress_success() throws Exception {
        AtomicBoolean reportSent = new AtomicBoolean(false);
        
        progressReporter.setHttpClient((url, data) -> {
            reportSent.set(true);
            assertTrue(url.contains("/api/batch/subtask/progress"));
            assertNotNull(data);
            return true; // 模拟HTTP 200
        });
        
        boolean result = progressReporter.reportProgress(1L, 50, 100);
        
        assertTrue(result, "上报应成功");
        assertTrue(reportSent.get(), "HTTP请求应被发送");
        
        System.out.println("✅ 进度上报成功: subtask=1, progress=50/100");
    }

    // ==================== 2. 上报失败 - 回退到本地 ====================

    @Test
    @DisplayName("2. reportProgress() 失败 - 回退到本地持久化")
    void testReportProgress_failure_fallbackToLocal() throws Exception {
        AtomicInteger fallbackCount = new AtomicInteger(0);
        
        progressReporter.setHttpClient((url, data) -> false); // 模拟失败
        
        progressReporter.setFallbackHandler(event -> {
            fallbackCount.incrementAndGet();
            assertEquals(2L, event.getSubtaskId().longValue());
        });
        
        boolean result = progressReporter.reportProgress(2L, 30, 100);
        
        assertFalse(result, "上报应失败");
        assertTrue(fallbackCount.get() >= 1, "失败后应至少回退到本地1次");
        
        System.out.println("✅ 上报失败回退: subtask=2, fallbackCount=" + fallbackCount.get());
    }

    // ==================== 3. 批量聚合上报 ====================

    @Test
    @DisplayName("3. batchReport() - 批量聚合")
    void testBatchReport_aggregation() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        List<ProgressEvent> receivedEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        
        progressReporter.setBatchHttpClient(events -> {
            callCount.incrementAndGet();
            receivedEvents.addAll(events);
            return true;
        });
        
        for (int i = 0; i < 5; i++) {
            progressReporter.addToBuffer(new ProgressEvent((long) i, i * 20, 100, System.currentTimeMillis()));
        }
        
        boolean result = progressReporter.flushBuffer();
        
        assertTrue(result, "批量上报应成功");
        assertEquals(1, callCount.get(), "应只调用一次HTTP");
        assertEquals(5, receivedEvents.size(), "应包含所有5个事件");
        
        System.out.println("✅ 批量聚合: 5个事件 → 1次HTTP请求");
    }

    // ==================== 4. 网络错误重试 ====================

    @Test
    @DisplayName("4. 网络错误 - 自动重试")
    void testNetworkError_retry() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        progressReporter.setHttpClient((url, data) -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("Network error"); // 前2次失败
            }
            return true; // 第3次成功
        });
        
        boolean result = progressReporter.reportProgressWithRetry(3L, 80, 100, 3);
        
        assertTrue(result, "重试后应成功");
        assertEquals(3, attemptCount.get(), "应尝试3次");
        
        System.out.println("✅ 网络错误重试: attempts=3, success on 3rd");
    }

    // ==================== 5. 并发上报安全 ====================

    @Test
    @DisplayName("5. 并发reportProgress() - 线程安全")
    void testConcurrentReports_threadSafe() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        progressReporter.setHttpClient((url, data) -> {
            try {
                Thread.sleep(50); // 模拟网络延迟
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        });
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final Long taskId = (long) i;
            final int progress = i * 10;
            executor.submit(() -> {
                try {
                    startLatch.countDown();
                    startLatch.await();
                    
                    if (progressReporter.reportProgress(taskId, progress, 100)) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.SECONDS);
        
        int total = successCount.get() + failCount.get();
        System.out.println("✅ 并发上报安全: threads=" + threadCount + 
                          ", success=" + successCount.get() + 
                          ", failed=" + failCount.get());
                          
        assertEquals(threadCount, total, "所有线程都应完成");
        assertTrue(successCount.get() > 0, "应有部分成功");
    }
}
