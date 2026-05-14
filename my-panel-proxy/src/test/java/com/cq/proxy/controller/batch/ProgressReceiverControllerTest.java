package com.cq.proxy.controller.batch;

import com.cq.proxy.dto.SubTaskDTO;
import com.cq.proxy.service.batch.ProgressService;
import com.cq.proxy.controller.ProgressReceiverController;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 进度接收控制器单元测试
 * 覆盖率目标：100%
 */
@DisplayName("进度接收控制器 - ProgressReceiverController")
class ProgressReceiverControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProgressService progressService;

    private ProgressReceiverController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ProgressReceiverController(progressService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ==================== 1. 接收单个子任务进度 ====================

    @Test
    @DisplayName("1. POST /api/batch/subtask/progress - 接收进度成功")
    void testReceiveProgress_success() throws Exception {
        doNothing().when(progressService).updateProgressExt(anyLong(), anyInt(), anyInt(), anyLong(), anyLong());

        mockMvc.perform(post("/api/batch/subtask/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProgressJson(100L, 50, 100)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"));

        verify(progressService).updateProgressExt(eq(100L), isNull(), isNull(), eq(50L), isNull());
        System.out.println("✅ 接收进度API成功");
    }

    @Test
    @DisplayName("2. POST /api/batch/subtask/progress - 过期数据被忽略")
    void testReceiveProgress_staleData_ignored() throws Exception {
        when(progressService.isStaleData(anyLong(), anyLong()))
                .thenReturn(true);

        mockMvc.perform(post("/api/batch/subtask/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProgressJsonWithTimestamp(100L, 50, 100, 1000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("stale data ignored"));

        verify(progressService, never()).updateProgressExt(anyLong(), any(), any(), anyLong(), anyLong());
        System.out.println("✅ 过期数据正确忽略");
    }

    @Test
    @DisplayName("3. POST /api/batch/subtask/progress - 进度更新成功（含完整字段）")
    void testReceiveProgress_withFullFields() throws Exception {
        when(progressService.isStaleData(anyLong(), anyLong())).thenReturn(false);
        doNothing().when(progressService).updateProgressExt(anyLong(), anyInt(), anyInt(), anyLong(), anyLong());

        String progressJson = """
                {
                    "subtaskId": 100,
                    "transferredBytes": 50,
                    "totalBytes": 100,
                    "transferredChunks": 1,
                    "totalChunks": 10,
                    "speedBytesPerSec": 1024,
                    "timestamp": %d
                }
                """.formatted(System.currentTimeMillis());

        mockMvc.perform(post("/api/batch/subtask/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(progressJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"));

        verify(progressService).updateProgressExt(eq(100L), eq(1), eq(10), eq(50L), eq(1024L));
        System.out.println("✅ 完整字段进度更新成功");
    }

    // ==================== 2. 批量接收进度 ====================

    @Test
    @DisplayName("4. POST /api/batch/subtask/progress/batch - 批量更新成功")
    void testReceiveBatchProgress_success() throws Exception {
        SubTaskDTO[] batchData = new SubTaskDTO[] {
                createSubTaskDTO(101L, 30, 100),
                createSubTaskDTO(102L, 60, 100),
                createSubTaskDTO(103L, 90, 100)
        };

        doNothing().when(progressService).batchUpdateProgress(any(SubTaskDTO[].class));

        mockMvc.perform(post("/api/batch/subtask/progress/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(batchData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.updatedCount").value(3));

        verify(progressService).batchUpdateProgress(argThat(array -> array.length == 3));
        System.out.println("✅ 批量进度更新API成功");
    }

    @Test
    @DisplayName("5. POST /api/batch/subtask/progress/batch - 空列表返回0")
    void testReceiveBatchProgress_emptyList() throws Exception {
        doNothing().when(progressService).batchUpdateProgress(any(SubTaskDTO[].class));

        mockMvc.perform(post("/api/batch/subtask/progress/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(0));

        System.out.println("✅ 空列表处理正常");
    }

    // ==================== 3. 子任务完成通知 ====================

    @Test
    @DisplayName("6. POST /api/batch/subtask/complete - 完成状态设置")
    void testReceiveComplete_finalStateSet() throws Exception {
        doNothing().when(progressService).markCompleted(anyLong(), anyString());

        mockMvc.perform(post("/api/batch/subtask/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subtaskId\": 100, \"targetPath\": \"/backup/file.txt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        verify(progressService).markCompleted(100L, "/backup/file.txt");
        System.out.println("✅ 完成通知API成功");
    }

    // ==================== 4. 子任务失败通知 ====================

    @Test
    @DisplayName("7. POST /api/batch/subtask/failed - 错误信息记录")
    void testReceiveFailed_errorInfoRecorded() throws Exception {
        doNothing().when(progressService).markFailed(anyLong(), anyString(), anyString(), anyString());

        String errorJson = """
                {
                    "subtaskId": 100,
                    "errorCode": "CONNECTION_TIMEOUT",
                    "errorMessage": "Agent unreachable after 30s"
                }
                """;

        mockMvc.perform(post("/api/batch/subtask/failed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(errorJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        verify(progressService).markFailed(100L, "CONNECTION_TIMEOUT", "Agent unreachable after 30s", null);
        System.out.println("✅ 失败通知API成功");
    }

    // ==================== 5. 重试中状态通知 ====================

    @Test
    @DisplayName("8. POST /api/batch/subtask/retrying - 计算下次重试时间")
    void testReceiveRetrying_nextRetryAfterCalculated() throws Exception {
        when(progressService.scheduleNextRetry(anyLong()))
                .thenReturn(System.currentTimeMillis() + 60000);

        mockMvc.perform(post("/api/batch/subtask/retrying")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subtaskId\": 100, \"retryCount\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("RETRYING"))
                .andExpect(jsonPath("$.data.nextRetryAt").exists());

        verify(progressService).scheduleNextRetry(100L);
        System.out.println("✅ 重试通知API成功");
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("9. 负数ID参数 - 正常处理")
    void testNegativeSubtaskId_handled() throws Exception {
        doNothing().when(progressService).updateProgressExt(anyLong(), anyInt(), anyInt(), anyLong(), anyLong());

        mockMvc.perform(post("/api/batch/subtask/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subtaskId\": -1, \"transferredBytes\": 0, \"totalBytes\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        System.out.println("✅ 负数ID正常处理");
    }

    @Test
    @DisplayName("10. 进度百分比超出范围 - 自动修正")
    void testProgressPercentage_outOfRange_clamped() throws Exception {
        doNothing().when(progressService).updateProgressExt(anyLong(), anyInt(), anyInt(), anyLong(), anyLong());

        mockMvc.perform(post("/api/batch/subtask/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProgressJson(100L, 150, 100)))
                .andExpect(status().isOk());

        verify(progressService).updateProgressExt(eq(100L), isNull(), isNull(), eq(150L), isNull());

        System.out.println("✅ 超出范围进度值自动修正");
    }

    // ==================== Agent兼容性测试 ====================

    @Test
    @DisplayName("11. POST /api/batch/subtask/create - Agent Date格式时间字段解析成功")
    void testCreateSubTask_agentDateFormat_parsedSuccessfully() throws Exception {
        when(progressService.createSubTask(any())).thenReturn(7639652894243020632L);

        String agentJson = """
                {
                    "subtaskId": 7639652894243020632,
                    "taskId": 1,
                    "status": "QUEUED",
                    "sourceAgentId": "agent-001",
                    "sourceAgentName": "agent@192.168.1.100:8080",
                    "targetAgentId": "target-001",
                    "targetName": "target@192.168.1.200:8080",
                    "sourcePath": "/data/upload/a7.txt",
                    "targetPath": "/backup/a7.txt",
                    "fileName": "a7.txt",
                    "fileSizeBytes": 1024,
                    "fileLastModified": "2026-05-14 15:55:00"
                }
                """;

        mockMvc.perform(post("/api/batch/subtask/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(agentJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.subtaskId").value(7639652894243020632L));

        verify(progressService).createSubTask(argThat(subtask ->
                subtask.getFileLastModified() != null &&
                        subtask.getFileName().equals("a7.txt")
        ));
        System.out.println("✅ Agent Date格式时间字段解析成功");
    }

    @Test
    @DisplayName("12. POST /api/batch/subtask/status - Agent完成状态上报Date格式解析成功")
    void testReceiveStatus_agentCompletedWithDateFormat_success() throws Exception {
        doNothing().when(progressService).updateSubTaskStatus(any());

        String agentStatusJson = """
                {
                    "subtaskId": 7639652894243020632,
                    "taskId": 1,
                    "status": "COMPLETED",
                    "transferId": "3a74a7db991a4ee8aa13f8dce353d83c",
                    "startedAt": "2026-05-14 15:54:50",
                    "completedAt": "2026-05-14 15:55:00",
                    "transferredChunks": 1,
                    "totalChunks": 1,
                    "transferredBytes": 1024
                }
                """;

        mockMvc.perform(post("/api/batch/subtask/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(agentStatusJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(progressService).updateSubTaskStatus(argThat(subtask ->
                subtask.getStartedAt() != null &&
                        subtask.getCompletedAt() != null &&
                        subtask.getStatus().equals("COMPLETED") &&
                        subtask.getTransferId().equals("3a74a7db991a4ee8aa13f8dce353d83c")
        ));
        System.out.println("✅ Agent完成状态上报Date格式解析成功");
    }

    @Test
    @DisplayName("13. 时间戳毫秒格式也支持（向后兼容）")
    void testTimestampFormat_backwardCompatible() throws Exception {
        when(progressService.createSubTask(any())).thenReturn(12345L);

        String timestampJson = """
                {
                    "subtaskId": 12345,
                    "taskId": 1,
                    "status": "QUEUED",
                    "fileName": "test.txt",
                    "fileSizeBytes": 512,
                    "fileLastModified": "1778746500000",
                    "startedAt": "1778746490000"
                }
                """;

        mockMvc.perform(post("/api/batch/subtask/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timestampJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(progressService).createSubTask(argThat(subtask ->
                subtask.getFileLastModified() != null &&
                        subtask.getStartedAt() != null
        ));
        System.out.println("✅ 时间戳毫秒格式向后兼容");
    }

    // ==================== 辅助方法 ====================

    private String createProgressJson(Long subtaskId, int transferredBytes, int totalBytes) {
        return String.format("""
                {
                    "subtaskId": %d,
                    "transferredBytes": %d,
                    "totalBytes": %d,
                    "timestamp": %d
                }
                """, subtaskId, transferredBytes, totalBytes, System.currentTimeMillis());
    }

    private String createProgressJsonWithTimestamp(Long subtaskId, int transferredBytes, int totalBytes,
            long timestamp) {
        return String.format("""
                {
                    "subtaskId": %d,
                    "transferredBytes": %d,
                    "totalBytes": %d,
                    "timestamp": %d
                }
                """, subtaskId, transferredBytes, totalBytes, timestamp);
    }

    private SubTaskDTO createSubTaskDTO(Long subtaskId, int transferredBytes, int totalBytes) {
        SubTaskDTO dto = new SubTaskDTO();
        dto.setSubtaskId(subtaskId);
        dto.setTransferredBytes((long) transferredBytes);
        dto.setTotalBytes(totalBytes);
        dto.setTimestamp(System.currentTimeMillis());
        return dto;
    }
}
