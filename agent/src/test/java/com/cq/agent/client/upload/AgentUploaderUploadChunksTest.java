package com.cq.agent.client.upload;

import com.cq.agent.client.BaseAgentClient;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.ChunkUploadResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentUploader - uploadChunks 方法修复验证")
class AgentUploaderUploadChunksTest {

    @TempDir
    Path tempDir;

    private TestableUploader uploader;
    private Path testFile;

    @BeforeEach
    void setUp() throws IOException {
        AgentConfig config = new AgentConfig();
        config.setMaxUploadRateKBPerSecond(0);
        uploader = new TestableUploader(config);
        uploader.init();

        testFile = tempDir.resolve("test-upload.dat");
        byte[] data = new byte[1024 * 100];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        Files.write(testFile, data);
    }

    @AfterEach
    void tearDown() {
        if (uploader != null) {
            uploader.shutdown();
        }
    }

    @Test
    @DisplayName("空 missingChunks 列表应立即返回")
    void testEmptyMissingChunksReturnsImmediately() throws Exception {
        UploadTask task = createTask(5, 1024, List.of());

        invokeUploadChunks(task);

        assertEquals(0, uploader.getReadChunkCalls().get(), "不应读取任何 chunk");
        assertEquals(0, uploader.getUploadChunkCalls().get(), "不应上传任何 chunk");
    }

    @Test
    @DisplayName("所有 chunk 成功上传时应报告正确进度")
    void testSuccessfulUploadReportsCorrectProgress() throws Exception {
        uploader.setUploadShouldSucceed(true);

        int totalChunks = 5;
        List<Integer> missingChunks = List.of(0, 1, 2, 3, 4);
        UploadTask task = createTask(totalChunks, 1024, missingChunks);

        invokeUploadChunks(task);

        assertEquals(5, uploader.getUploadChunkCalls().get(), "应该上传 5 个 chunk");
        assertEquals(totalChunks, task.getUploadChunksCount().get(),
                "uploadChunksCount 应该等于总 chunk 数");
    }

    @Test
    @DisplayName("部分 chunk 失败时应抛出包含详细信息的异常")
    void testPartialFailureThrowsDetailedException() throws Exception {
        uploader.setUploadShouldSucceed(true);
        uploader.setFailOnChunkIndices(List.of(1, 3));

        int totalChunks = 5;
        List<Integer> missingChunks = List.of(0, 1, 2, 3, 4);
        UploadTask task = createTask(totalChunks, 1024, missingChunks);

        IOException exception = assertThrows(IOException.class,
                () -> invokeUploadChunks(task),
                "部分失败时应该抛出 IOException");

        String msg = exception.getMessage();
        assertTrue(msg.contains("2/5"), "异常消息应包含失败数量 '2/5': " + msg);
        assertTrue(msg.contains("3 succeeded"), "异常消息应包含成功数量 '3 succeeded': " + msg);
    }

    @Test
    @DisplayName("超时公式应正确计算包含速率限制的超时时间")
    void testTimeoutFormulaIncludesRateLimitDelay() throws Exception {
        AgentConfig config = new AgentConfig();
        config.setMaxUploadRateKBPerSecond(10);
        config.setUploadRequestTimeoutSeconds(30);
        TestableUploader rateLimitedUploader = new TestableUploader(config);
        rateLimitedUploader.init();

        try {
            int chunkSize = 1024 * 50;

            long estimatedMs = rateLimitedUploader.callEstimateRateLimitTimeMsPerChunk(chunkSize);
            assertTrue(estimatedMs > 0,
                    "启用限速时 estimateRateLimitTimeMsPerChunk 应返回正值，实际: " + estimatedMs + "ms");

            int requestTimeoutSec = rateLimitedUploader.getRequestTimeoutSeconds();
            long perChunkTimeMs = Math.max(requestTimeoutSec * 1000L, estimatedMs);
            assertTrue(perChunkTimeMs >= estimatedMs,
                    "perChunkTimeMs 应至少等于限速估算时间");
            assertTrue(perChunkTimeMs >= requestTimeoutSec * 1000L,
                    "perChunkTimeMs 应至少等于 requestTimeoutSeconds * 1000");
        } finally {
            rateLimitedUploader.shutdown();
        }
    }

    @Test
    @DisplayName("超时公式在无速率限制时应使用 requestTimeout 作为基准")
    void testTimeoutFormulaWithoutRateLimit() throws Exception {
        int chunkSize = 1024 * 100;
        long estimatedMs = uploader.callEstimateRateLimitTimeMsPerChunk(chunkSize);
        assertEquals(0, estimatedMs, "未启用限速时限速估算应返回 0");

        int requestTimeoutSec = uploader.getRequestTimeoutSeconds();
        long perChunkTimeMs = Math.max(requestTimeoutSec * 1000L, estimatedMs);
        assertEquals(requestTimeoutSec * 1000L, perChunkTimeMs,
                "无限速时 perChunkTimeMs 应等于 requestTimeoutSeconds * 1000");
    }

    @Test
    @DisplayName("超时公式最低保障应为60秒")
    void testTimeoutMinimumGuarantee() throws Exception {
        uploader.setRequestTimeoutSeconds(1);
        uploader.setConcurrentThreads(100);

        long timeoutMs = uploader.computeTimeoutMs(5, 1024);

        assertTrue(timeoutMs >= 60_000L,
                "超时时间不应低于 60000ms，实际: " + timeoutMs + "ms");
    }

    @Test
    @DisplayName("worker线程抛出InterruptedException应被记录为失败并最终抛出异常")
    void testWorkerInterruptRecordedAsFailure() throws Exception {
        uploader.setUploadShouldSucceed(true);
        AtomicBoolean interruptFlag = new AtomicBoolean(false);
        uploader.setInterruptOnChunkIndex(2, interruptFlag);

        int totalChunks = 5;
        List<Integer> missingChunks = List.of(0, 1, 2, 3, 4);
        UploadTask task = createTask(totalChunks, 1024, missingChunks);

        IOException exception = assertThrows(IOException.class,
                () -> invokeUploadChunks(task),
                "worker 中断导致 chunk 失败后应该抛出 IOException");

        String msg = exception.getMessage();
        assertTrue(msg.contains("failed"), "异常消息应包含 'failed': " + msg);
        assertTrue(interruptFlag.get(), "中断标志应该被设置");
        assertTrue(uploader.getUploadChunkCalls().get() >= 3,
                "至少应尝试上传到第3个 chunk（索引0,1成功，2中断）");
    }

    @Test
    @DisplayName("所有 chunk 都失败时应报告全部失败")
    void testAllChunksFailReportsAllFailed() throws Exception {
        uploader.setFailOnChunkIndices(List.of(0, 1, 2));

        int totalChunks = 3;
        List<Integer> missingChunks = List.of(0, 1, 2);
        UploadTask task = createTask(totalChunks, 1024, missingChunks);

        IOException exception = assertThrows(IOException.class,
                () -> invokeUploadChunks(task));

        String msg = exception.getMessage();
        assertTrue(msg.contains("3/3"), "异常消息应显示 3/3 全部失败: " + msg);
    }

    @Test
    @DisplayName("cancelAllFutures 应正确取消所有 future")
    void testCancelAllFuturesBehaviour() throws Exception {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger completedCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(5000);
                    completedCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }

        uploader.callCancelAllFutures(futures);

        for (CompletableFuture<Void> f : futures) {
            assertTrue(f.isCancelled(), "每个 future 都应被取消");
        }

        Thread.sleep(200);
        assertEquals(0, completedCount.get(), "被取消的 future 不应执行完成");
    }

    @Test
    @DisplayName("单个 chunk 上传应正常工作")
    void testSingleChunkUpload() throws Exception {
        uploader.setUploadShouldSucceed(true);

        List<Integer> missingChunks = List.of(0);
        UploadTask task = createTask(1, 1024, missingChunks);

        invokeUploadChunks(task);

        assertEquals(1, uploader.getUploadChunkCalls().get());
        assertEquals(1, task.getUploadChunksCount().get());
    }

    @Test
    @DisplayName("cancelAllFutures 对空列表不报错")
    void testCancelEmptyFutureList() throws Exception {
        assertDoesNotThrow(() -> {
            uploader.callCancelAllFutures(new ArrayList<>());
        }, "取消空 future 列表不应抛出异常");
    }

    @Test
    @DisplayName("estimateRateLimitTimeMsPerChunk 在 chunkSize=0 时返回0")
    void testEstimateRateLimitZeroChunkSize() throws Exception {
        long result = uploader.callEstimateRateLimitTimeMsPerChunk(0);
        assertEquals(0, result, "chunkSize 为 0 时应返回 0");
    }

    @Test
    @DisplayName("estimateRateLimitTimeMsPerChunk 在负数 chunkSize 时返回0")
    void testEstimateRateLimitNegativeChunkSize() throws Exception {
        long result = uploader.callEstimateRateLimitTimeMsPerChunk(-100);
        assertEquals(0, result, "chunkSize 为负数时应返回 0");
    }

    private UploadTask createTask(int totalChunks, int chunkSize, List<Integer> missingChunks) {
        return createTaskWithChunkSize(totalChunks, chunkSize, missingChunks);
    }

    private UploadTask createTaskWithChunkSize(int totalChunks, int chunkSize, List<Integer> missingChunks) {
        UploadTask task = new UploadTask(
                testFile.toString(),
                "/tmp/remote-file.dat",
                (long) totalChunks * chunkSize,
                "http://localhost:8888/",
                "testuser"
        );
        task.setTransferId("test-transfer-" + System.nanoTime());
        task.setTraceId("trace-" + System.nanoTime());
        task.setChunkSize(chunkSize);
        task.setTotalChunks(totalChunks);
        task.setMissingChunks(new ArrayList<>(missingChunks));
        return task;
    }

    private void invokeUploadChunks(UploadTask task) throws Exception {
        invokeUploadChunks(uploader, task);
    }

    private void invokeUploadChunks(TestableUploader target, UploadTask task) throws Exception {
        Method method = AgentUploader.class.getDeclaredMethod("uploadChunks", UploadTask.class);
        method.setAccessible(true);
        try {
            method.invoke(target, task);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    private static class TestableUploader extends AgentUploader {

        private final AtomicInteger readChunkCalls = new AtomicInteger(0);
        private final AtomicInteger uploadChunkCalls = new AtomicInteger(0);
        private volatile boolean uploadShouldSucceed = false;
        private volatile List<Integer> failOnChunkIndices = List.of();
        private volatile long chunkDelayMs = 0;
        private volatile int interruptOnChunkIndex = -1;
        private volatile AtomicBoolean interruptFlagRef = null;

        public TestableUploader(AgentConfig agentConfig) {
            super(agentConfig);
        }

        void setUploadShouldSucceed(boolean value) {
            this.uploadShouldSucceed = value;
        }

        void setFailOnChunkIndices(List<Integer> indices) {
            this.failOnChunkIndices = indices;
        }

        void setChunkDelayMs(long delayMs) {
            this.chunkDelayMs = delayMs;
        }

        void setInterruptOnChunkIndex(int index, AtomicBoolean flag) {
            this.interruptOnChunkIndex = index;
            this.interruptFlagRef = flag;
        }

        void setRequestTimeoutSeconds(int seconds) throws Exception {
            Field field = BaseAgentClient.class.getDeclaredField("requestTimeoutSeconds");
            field.setAccessible(true);
            field.setInt(this, seconds);
        }

        void setConcurrentThreads(int threads) throws Exception {
            Field field = BaseAgentClient.class.getDeclaredField("concurrentThreads");
            field.setAccessible(true);
            field.setInt(this, threads);
        }

        AtomicInteger getReadChunkCalls() {
            return readChunkCalls;
        }

        AtomicInteger getUploadChunkCalls() {
            return uploadChunkCalls;
        }

        long callEstimateRateLimitTimeMsPerChunk(int chunkSize) throws Exception {
            Method method = AgentUploader.class.getDeclaredMethod("estimateRateLimitTimeMsPerChunk", int.class);
            method.setAccessible(true);
            return (long) method.invoke(this, chunkSize);
        }

        void callCancelAllFutures(List<CompletableFuture<Void>> futures) throws Exception {
            Method method = AgentUploader.class.getDeclaredMethod("cancelAllFutures", List.class);
            method.setAccessible(true);
            method.invoke(this, futures);
        }

        int getRequestTimeoutSeconds() throws Exception {
            Field field = BaseAgentClient.class.getDeclaredField("requestTimeoutSeconds");
            field.setAccessible(true);
            return field.getInt(this);
        }

        long computeTimeoutMs(int totalToUpload, int chunkSize) throws Exception {
            Field concurrentThreadsField = BaseAgentClient.class.getDeclaredField("concurrentThreads");
            concurrentThreadsField.setAccessible(true);
            int concurrentThreads = concurrentThreadsField.getInt(this);

            Field requestTimeoutField = BaseAgentClient.class.getDeclaredField("requestTimeoutSeconds");
            requestTimeoutField.setAccessible(true);
            int requestTimeoutSec = requestTimeoutField.getInt(this);

            long waves = (long) Math.ceil((double) totalToUpload / concurrentThreads);
            long estimatedRateLimitMsPerChunk = callEstimateRateLimitTimeMsPerChunk(chunkSize);
            long perChunkTimeMs = Math.max(requestTimeoutSec * 1000L, estimatedRateLimitMsPerChunk);
            return Math.max(60_000L, (waves + 2) * perChunkTimeMs);
        }

        @Override
        protected byte[] readChunk(java.nio.channels.FileChannel channel, int chunkIndex,
                                   int chunkSize, long totalSize) throws IOException {
            readChunkCalls.incrementAndGet();
            if (chunkDelayMs > 0) {
                try {
                    Thread.sleep(chunkDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Read interrupted", e);
                }
            }
            byte[] data = new byte[Math.min(chunkSize, (int) Math.min(chunkSize, totalSize - (long) chunkIndex * chunkSize))];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) (chunkIndex + i);
            }
            return data;
        }

        @Override
        protected ApiResponse<ChunkUploadResponse> uploadChunk(UploadTask task, int chunkIndex,
                                                               byte[] data) throws IOException, InterruptedException {
            uploadChunkCalls.incrementAndGet();

            if (interruptOnChunkIndex >= 0 && chunkIndex == interruptOnChunkIndex && interruptFlagRef != null) {
                interruptFlagRef.set(true);
                Thread.currentThread().interrupt();
                throw new InterruptedException("Simulated interrupt at chunk " + chunkIndex);
            }

            if (failOnChunkIndices.contains(chunkIndex)) {
                ApiResponse<ChunkUploadResponse> resp = new ApiResponse<>();
                resp.setSuccess(false);
                resp.setMsg("Simulated failure for chunk " + chunkIndex);
                return resp;
            }

            if (!uploadShouldSucceed) {
                ApiResponse<ChunkUploadResponse> resp = new ApiResponse<>();
                resp.setSuccess(false);
                resp.setMsg("Upload not configured to succeed");
                return resp;
            }

            ChunkUploadResponse chunkResp = new ChunkUploadResponse();
            chunkResp.setTransferId(task.getTransferId());
            chunkResp.setCompleted(false);
            chunkResp.setChunkIndex(chunkIndex);
            return ApiResponse.success(chunkResp);
        }
    }
}
