package com.cq.agent.batch.tracker;

import com.cq.agent.batch.scanner.ScannedFile;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.TransferConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileBatchCompletionTracker - 文件批次完成追踪器")
class FileBatchCompletionTrackerTest {

    @TempDir
    Path tempDir;

    private FileBatchCompletionTracker tracker;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @BeforeEach
    void setUp() {
        tracker = new FileBatchCompletionTracker(tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        tracker = null;
    }

    private ScannedFile createScannedFile(String fileName) {
        ScannedFile file = new ScannedFile();
        file.setFileName(fileName);
        file.setFileSize(1024L);
        file.setLastModified(System.currentTimeMillis());
        file.setAbsolutePath("/data/logs/" + fileName);
        return file;
    }

    private AgentTaskConfig createTaskConfig() {
        AgentTaskConfig config = new AgentTaskConfig();
        config.setTaskId(1001L);
        config.setTaskName("测试任务");
        config.setSourceAgentId("src-agent-001");
        config.setSourceAgentName("root@10.0.0.1:7777");
        config.setSourceDir("/data/logs");

        TransferConfig tc = new TransferConfig();
        tc.setTransferMode("ONE_TO_MANY");
        tc.setRoutingStrategy("BROADCAST");
        tc.setPostTransferAction("DELETE");
        config.setTransferConfig(tc);

        return config;
    }

    private List<TargetAgentInfo> createTargets(int count) {
        List<TargetAgentInfo> targets = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            TargetAgentInfo target = new TargetAgentInfo();
            target.setAgentId("target-" + String.format("%03d", i));
            target.setAgentName("root@10.0.0." + (i + 1) + ":7777");
            target.setTargetDir("/data/remote/node" + i);
            targets.add(target);
        }
        return targets;
    }

    @Test
    @DisplayName("initFileBatch - 应该创建JSON文件，status=PENDING, totalTargets正确, 包含完整信息")
    void shouldCreateJsonFileOnInit() throws Exception {
        Long fileBatchId = 1001L;
        ScannedFile scannedFile = createScannedFile("app.log");
        AgentTaskConfig config = createTaskConfig();
        List<TargetAgentInfo> targets = createTargets(3);

        tracker.initFileBatch(fileBatchId, scannedFile, config, targets);

        Path jsonPath = tempDir.resolve("fb-" + fileBatchId + ".json");
        assertTrue(Files.exists(jsonPath), "JSON文件应该存在");

        FileBatchState state = tracker.loadFileBatch(fileBatchId);
        assertNotNull(state);
        assertEquals("PENDING", state.getStatus(), "初始状态应该是PENDING");
        assertEquals(3, state.getSummary().getTotalTargets(), "totalTargets应该等于目标数量");
        assertEquals(0, state.getSummary().getCompletedCount(), "初始completedCount应为0");
        assertEquals(0, state.getSummary().getFailedCount(), "初始failedCount应为0");
        assertEquals(3, state.getSummary().getPendingCount(), "初始pendingCount应等于totalTargets");

        assertNotNull(state.getSource(), "source信息不应为null");
        assertEquals("src-agent-001", state.getSource().getAgentId());
        assertEquals("app.log", state.getSource().getFileName());
        assertEquals(1024L, state.getSource().getFileSizeBytes());

        assertNotNull(state.getTransferConfig(), "transferConfig不应为null");
        assertEquals("ONE_TO_MANY", state.getTransferConfig().getTransferMode());
        assertEquals("BROADCAST", state.getTransferConfig().getRoutingStrategy());

        assertNotNull(state.getTargets(), "targets列表不应为null");
        assertEquals(3, state.getTargets().size());
        for (FileBatchState.TargetProgress tp : state.getTargets()) {
            assertEquals("PENDING", tp.getStatus(), "每个target初始状态应为PENDING");
            assertNotNull(tp.getTargetPath(), "targetPath应该被设置");
        }
    }

    @Test
    @DisplayName("markCompleted 单个完成 - 更新对应target为COMPLETED, completedCount+1, 返回false")
    void shouldMarkSingleTargetCompleted() throws Exception {
        Long fileBatchId = 1002L;
        tracker.initFileBatch(fileBatchId, createScannedFile("test.log"), createTaskConfig(), createTargets(3));

        boolean allCompleted = tracker.markCompleted(fileBatchId, "target-001");

        assertFalse(allCompleted, "单个完成后应返回false");

        FileBatchState state = tracker.loadFileBatch(fileBatchId);
        FileBatchState.TargetProgress target001 = state.getTargets().stream()
                .filter(t -> t.getAgentId().equals("target-001"))
                .findFirst()
                .orElseThrow();
        assertEquals("COMPLETED", target001.getStatus(), "target-001应该标记为COMPLETED");
        assertNotNull(target001.getCompletedAt(), "completedAt时间应该被设置");

        assertEquals(1, state.getSummary().getCompletedCount(), "completedCount应为1");
        assertEquals(2, state.getSummary().getPendingCount(), "pendingCount应为2");
        assertEquals("PENDING", state.getStatus(), "整体状态仍为PENDING");
    }

    @Test
    @DisplayName("markCompleted 全部完成 - 最后一个target完成后返回true, 整体status=COMPLETED")
    void shouldReturnTrueWhenAllCompleted() throws Exception {
        Long fileBatchId = 1003L;
        tracker.initFileBatch(fileBatchId, createScannedFile("full.log"), createTaskConfig(), createTargets(2));

        tracker.markCompleted(fileBatchId, "target-001");
        boolean allDone = tracker.markCompleted(fileBatchId, "target-002");

        assertTrue(allDone, "全部完成后应返回true");

        FileBatchState state = tracker.loadFileBatch(fileBatchId);
        assertEquals("COMPLETED", state.getStatus(), "整体状态应为COMPLETED");
        assertEquals(2, state.getSummary().getCompletedCount(), "completedCount应等于totalTargets");
        assertEquals(0, state.getSummary().getPendingCount(), "pendingCount应为0");

        for (FileBatchState.TargetProgress tp : state.getTargets()) {
            assertEquals("COMPLETED", tp.getStatus(), "所有target都应是COMPLETED");
        }
    }

    @Test
    @DisplayName("markFailed 终态失败(isFinalFailure=true) - 整体status变为FAILED, target状态为FINAL_FAILURE")
    void shouldMarkFinalFailure() throws Exception {
        Long fileBatchId = 1004L;
        tracker.initFileBatch(fileBatchId, createScannedFile("fail.log"), createTaskConfig(), createTargets(3));

        tracker.markFailed(fileBatchId, "target-002", true);

        FileBatchState state = tracker.loadFileBatch(fileBatchId);
        assertEquals("FAILED", state.getStatus(), "终态失败时整体状态应为FAILED");

        FileBatchState.TargetProgress failedTarget = state.getTargets().stream()
                .filter(t -> t.getAgentId().equals("target-002"))
                .findFirst()
                .orElseThrow();
        assertEquals("FINAL_FAILURE", failedTarget.getStatus(), "target状态应为FINAL_FAILURE");

        assertEquals(1, state.getSummary().getFailedCount(), "failedCount应为1");
    }

    @Test
    @DisplayName("markFailed 非终态失败(isFinalFailure=false) - 整体status保持PENDING, target状态为FAILED")
    void shouldMarkNonFinalFailure() throws Exception {
        Long fileBatchId = 1005L;
        tracker.initFileBatch(fileBatchId, createScannedFile("retry.log"), createTaskConfig(), createTargets(3));

        tracker.markFailed(fileBatchId, "target-001", false);

        FileBatchState state = tracker.loadFileBatch(fileBatchId);
        assertEquals("PENDING", state.getStatus(), "非终态失败时整体状态应保持PENDING");

        FileBatchState.TargetProgress failedTarget = state.getTargets().stream()
                .filter(t -> t.getAgentId().equals("target-001"))
                .findFirst()
                .orElseThrow();
        assertEquals("FAILED", failedTarget.getStatus(), "非终态失败时target状态应为FAILED");

        assertEquals(1, state.getSummary().getFailedCount(), "failedCount应为1");
    }

    @Test
    @DisplayName("重试回退 - FAILED → markCompleted后回滚为COMPLETED, summary正确更新")
    void shouldRetryFromFailedToCompleted() throws Exception {
        Long fileBatchId = 1006L;
        tracker.initFileBatch(fileBatchId, createScannedFile("retry.log"), createTaskConfig(), createTargets(2));

        tracker.markFailed(fileBatchId, "target-001", false);
        FileBatchState afterFail = tracker.loadFileBatch(fileBatchId);
        assertEquals(1, afterFail.getSummary().getFailedCount());

        tracker.markCompleted(fileBatchId, "target-001");
        FileBatchState afterRetry = tracker.loadFileBatch(fileBatchId);

        FileBatchState.TargetProgress retriedTarget = afterRetry.getTargets().stream()
                .filter(t -> t.getAgentId().equals("target-001"))
                .findFirst()
                .orElseThrow();
        assertEquals("COMPLETED", retriedTarget.getStatus(), "重试成功后状态应为COMPLETED");

        assertEquals(1, afterRetry.getSummary().getCompletedCount(), "completedCount应为1");
        assertEquals(0, afterRetry.getSummary().getFailedCount(), "failedCount应回退为0");
        assertEquals(1, afterRetry.getSummary().getPendingCount(), "pendingCount应为1");
    }

    @Test
    @DisplayName("deleteFileBatch - JSON文件应该被删除")
    void shouldDeleteJsonFile() throws Exception {
        Long fileBatchId = 1007L;
        tracker.initFileBatch(fileBatchId, createScannedFile("delete.log"), createTaskConfig(), createTargets(2));

        Path jsonPath = tempDir.resolve("fb-" + fileBatchId + ".json");
        assertTrue(Files.exists(jsonPath), "删除前文件应存在");

        tracker.deleteFileBatch(fileBatchId);

        assertFalse(Files.exists(jsonPath), "删除后文件不应存在");
        assertThrows(Exception.class, () -> tracker.loadFileBatch(fileBatchId),
                "加载已删除的批次应抛出异常");
    }

    @Test
    @DisplayName("recoverPendingBatches - 应扫描到PENDING和FAILED状态的残留文件（不含COMPLETED）")
    void shouldRecoverPendingAndFailedBatches() throws Exception {
        tracker.initFileBatch(2001L, createScannedFile("pending.log"), createTaskConfig(), createTargets(2));
        tracker.initFileBatch(2002L, createScannedFile("failed.log"), createTaskConfig(), createTargets(2));
        tracker.initFileBatch(2003L, createScannedFile("completed.log"), createTaskConfig(), createTargets(1));

        tracker.markFailed(2002L, "target-001", true);
        tracker.markCompleted(2003L, "target-001");

        List<FileBatchState> recovered = tracker.recoverPendingBatches();

        assertEquals(2, recovered.size(), "应恢复2个非COMPLETED的批次");
        assertTrue(recovered.stream().anyMatch(s -> s.getFileBatchId().equals(2001L)),
                "应包含PENDING状态的批次");
        assertTrue(recovered.stream().anyMatch(s -> s.getFileBatchId().equals(2002L)),
                "应包含FAILED状态的批次");
        assertFalse(recovered.stream().anyMatch(s -> s.getFileBatchId().equals(2003L)),
                "不应包含COMPLETED状态的批次");
    }

    @Test
    @DisplayName("并发安全 - 多线程同时markCompleted，最终结果一致")
    void shouldBeThreadSafeForConcurrentMarkCompleted() throws Exception {
        Long fileBatchId = 3001L;
        int targetCount = 5;
        tracker.initFileBatch(fileBatchId, createScannedFile("concurrent.log"), createTaskConfig(), createTargets(targetCount));

        ExecutorService executor = Executors.newFixedThreadPool(targetCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 1; i <= targetCount; i++) {
            final String agentId = "target-" + String.format("%03d", i);
            executor.submit(() -> {
                try {
                    latch.await();
                    boolean result = tracker.markCompleted(fileBatchId, agentId);
                    if (result) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "线程应在超时前完成");

        assertEquals(1, successCount.get(), "只有一个线程应收到allCompleted=true信号");

        FileBatchState state = tracker.loadFileBatch(fileBatchId);
        assertEquals("COMPLETED", state.getStatus(), "并发完成后整体状态应为COMPLETED");
        assertEquals(targetCount, state.getSummary().getCompletedCount(),
                "所有target都应被标记为COMPLETED");
        assertEquals(0, state.getSummary().getPendingCount(), "pendingCount应为0");
    }
}
