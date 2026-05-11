package com.cq.agent.batch.report;

import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProgressReporter符合spec.md的单元测试
 * 验证上报格式、重试策略符合设计要求
 */
@DisplayName("进度上报器 - 符合spec.md设计")
class ProgressReporterSpecTest {

    private ProgressReporter progressReporter;

    @BeforeEach
    void setUp() {
        progressReporter = new ProgressReporter("http://localhost:9876");
    }

    @Test
    @DisplayName("1. reportProgress() - 上报格式符合spec.md要求")
    void testReportProgress_format() {
        AtomicReference<String> capturedData = new AtomicReference<>();

        progressReporter.setHttpClient((url, data) -> {
            capturedData.set(data);
            return true;
        });

        ProgressEvent event = new ProgressEvent(
            1L, 100L, "transfer-123", "SENDING",
            15, 20, 15728640L, 5242880L,
            System.currentTimeMillis(), 1
        );

        boolean result = progressReporter.reportProgress(event);

        assertTrue(result, "上报应成功");
        String data = capturedData.get();
        assertNotNull(data);

        // 验证JSON格式包含spec要求的所有字段
        assertTrue(data.contains("\"subtaskId\":1"), "应包含subtaskId");
        assertTrue(data.contains("\"taskId\":100"), "应包含taskId");
        assertTrue(data.contains("\"transferId\":\"transfer-123\""), "应包含transferId");
        assertTrue(data.contains("\"status\":\"SENDING\""), "应包含status");
        assertTrue(data.contains("\"transferredChunks\":15"), "应包含transferredChunks");
        assertTrue(data.contains("\"totalChunks\":20"), "应包含totalChunks");
        assertTrue(data.contains("\"transferredBytes\":15728640"), "应包含transferredBytes");
        assertTrue(data.contains("\"speedBytesPerSec\":5242880"), "应包含speedBytesPerSec");
        assertTrue(data.contains("\"sequenceNumber\":1"), "应包含sequenceNumber");
        assertTrue(data.contains("\"timestamp\""), "应包含timestamp");

        System.out.println("✅ 上报格式符合spec: " + data);
    }

    @Test
    @DisplayName("2. reportStatusChange() - 状态变更上报")
    void testReportStatusChange() {
        AtomicReference<String> capturedData = new AtomicReference<>();

        progressReporter.setHttpClient((url, data) -> {
            capturedData.set(data);
            return true;
        });

        ProgressEvent event = new ProgressEvent(
            1L, 100L, "transfer-123", "COMPLETED",
            20, 20, 20971520L, 0L,
            System.currentTimeMillis(), 2
        );

        boolean result = progressReporter.reportProgress(event);

        assertTrue(result);
        String data = capturedData.get();
        assertTrue(data.contains("\"status\":\"COMPLETED\""));
        assertTrue(data.contains("\"transferredChunks\":20"));
        assertTrue(data.contains("\"totalChunks\":20"));

        System.out.println("✅ 状态变更上报: COMPLETED");
    }

    @Test
    @DisplayName("3. 上报失败 - 4xx错误不重试")
    void testReportProgress_4xxNoRetry() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        progressReporter.setHttpClient((url, data) -> {
            attemptCount.incrementAndGet();
            return false; // 模拟4xx错误
        });

        ProgressEvent event = new ProgressEvent(
            1L, 100L, "transfer-123", "SENDING",
            15, 20, 15728640L, 5242880L,
            System.currentTimeMillis(), 1
        );

        boolean result = progressReporter.reportProgressWithRetry(event, 3);

        assertFalse(result, "应返回失败");
        assertEquals(1, attemptCount.get(), "4xx错误不应重试");

        System.out.println("✅ 4xx错误不重试: attempts=1");
    }

    @Test
    @DisplayName("4. 上报失败 - 网络超时重试3次")
    void testReportProgress_networkRetry() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        progressReporter.setHttpClient((url, data) -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("Connection timeout"); // 模拟网络超时
            }
            return true; // 第3次成功
        });

        ProgressEvent event = new ProgressEvent(
            1L, 100L, "transfer-123", "SENDING",
            15, 20, 15728640L, 5242880L,
            System.currentTimeMillis(), 1
        );

        boolean result = progressReporter.reportProgressWithRetry(event, 3);

        assertTrue(result, "重试后应成功");
        assertEquals(3, attemptCount.get(), "应尝试3次");

        System.out.println("✅ 网络超时重试: attempts=3, success on 3rd");
    }

    @Test
    @DisplayName("5. 上报失败 - 超过最大重试次数后回退到本地")
    void testReportProgress_maxRetryFallback() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);

        progressReporter.setHttpClient((url, data) -> {
            attemptCount.incrementAndGet();
            throw new RuntimeException("Network unreachable");
        });

        progressReporter.setFallbackHandler(event -> {
            fallbackCalled.set(true);
        });

        ProgressEvent event = new ProgressEvent(
            1L, 100L, "transfer-123", "SENDING",
            15, 20, 15728640L, 5242880L,
            System.currentTimeMillis(), 1
        );

        boolean result = progressReporter.reportProgressWithRetry(event, 3);

        assertFalse(result, "应返回失败");
        assertEquals(3, attemptCount.get(), "应尝试3次");
        assertTrue(fallbackCalled.get(), "超过重试次数后应回退到本地");

        System.out.println("✅ 超过重试次数回退本地: attempts=3, fallback=true");
    }

    @Test
    @DisplayName("6. 批量上报 - 使用progress/batch接口")
    void testBatchReport() {
        AtomicReference<String> capturedUrl = new AtomicReference<>();
        AtomicInteger eventCount = new AtomicInteger(0);

        progressReporter.setBatchHttpClient(events -> {
            eventCount.set(events.size());
            return true;
        });

        for (int i = 0; i < 5; i++) {
            progressReporter.addToBuffer(new ProgressEvent(
                (long) i, 100L, "transfer-" + i, "SENDING",
                i * 2, 10, i * 2097152L, 1048576L,
                System.currentTimeMillis(), i
            ));
        }

        boolean result = progressReporter.flushBuffer();

        assertTrue(result, "批量上报应成功");
        assertEquals(5, eventCount.get(), "应上报5个事件");

        System.out.println("✅ 批量上报: 5个事件");
    }
}
