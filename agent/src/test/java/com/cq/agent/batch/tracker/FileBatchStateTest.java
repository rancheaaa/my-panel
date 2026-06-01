package com.cq.agent.batch.tracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileBatchState - 文件批次状态数据模型")
class FileBatchStateTest {

    @Test
    @DisplayName("应该正确构建完整的FileBatchState对象")
    void shouldConstructCompleteState() {
        FileBatchState state = new FileBatchState();
        state.setFileBatchId(12345L);
        state.setTaskId(1001L);
        state.setTaskName("测试任务");
        state.setScanBatchId(999L);

        FileBatchState.SourceInfo source = new FileBatchState.SourceInfo();
        source.setAgentId("src-agent");
        source.setAgentName("root@10.0.0.1:7777");
        source.setSourceDir("/data/logs");
        source.setFilePath("/data/logs/app.log");
        source.setFileName("app.log");
        source.setFileSizeBytes(1024L);
        source.setLastModified("2026-05-18 10:00:00.000");
        state.setSource(source);

        FileBatchState.TransferConfigInfo tc = new FileBatchState.TransferConfigInfo();
        tc.setTransferMode("ONE_TO_MANY");
        tc.setRoutingStrategy("BROADCAST");
        tc.setPostTransferAction("DELETE");
        state.setTransferConfig(tc);

        assertEquals(12345L, state.getFileBatchId());
        assertEquals("DELETE", state.getTransferConfig().getPostTransferAction());
        assertEquals("app.log", state.getSource().getFileName());
    }

    @Test
    @DisplayName("TargetProgress应该正确记录单节点信息")
    void shouldHoldTargetProgress() {
        FileBatchState.TargetProgress tp = new FileBatchState.TargetProgress();
        tp.setAgentId("target-001");
        tp.setAgentName("root@10.0.0.2:7777");
        tp.setTargetDir("/data/remote");
        tp.setTargetPath("/data/remote/app.log");
        tp.setStatus("PENDING");

        assertEquals("PENDING", tp.getStatus());
        assertNull(tp.getCompletedAt());
    }

    @Test
    @DisplayName("Summary应该正确计算汇总统计")
    void shouldCalculateSummary() {
        FileBatchState.Summary summary = new FileBatchState.Summary();
        summary.setTotalTargets(3);
        summary.setCompletedCount(2);
        summary.setFailedCount(0);
        summary.setPendingCount(1);

        assertEquals(3, summary.getTotalTargets());
        assertEquals(1, summary.getPendingCount());
    }

    @Test
    @DisplayName("所有字段默认值应为null或零值")
    void shouldHaveCorrectDefaultValues() {
        FileBatchState state = new FileBatchState();
        assertNull(state.getFileBatchId());
        assertNull(state.getStatus());
        assertNull(state.getSource());
        assertNull(state.getTransferConfig());
        assertNull(state.getTargets());
        assertNull(state.getSummary());

        FileBatchState.TargetProgress tp = new FileBatchState.TargetProgress();
        assertNull(tp.getStatus());
        assertNull(tp.getCompletedAt());

        FileBatchState.Summary summary = new FileBatchState.Summary();
        assertEquals(0, summary.getTotalTargets());
        assertEquals(0, summary.getCompletedCount());
    }
}
