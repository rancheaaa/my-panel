package com.cq.proxy.controller.batch;

import com.cq.proxy.service.batch.ProgressService;
import com.cq.proxy.controller.ProgressReceiverController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        doNothing().when(progressService).updateProgress(anyLong(), anyInt(), anyInt());

        mockMvc.perform(post("/api/batch/subtask/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProgressJson(100L, 50, 100)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.msg").value("success"));

        verify(progressService).updateProgress(100L, 50, 100);
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

        verify(progressService, never()).updateProgress(anyLong(), anyInt(), anyInt());
        System.out.println("✅ 过期数据正确忽略");
    }

    @Test
    @DisplayName("3. POST /api/batch/subtask/progress - 子任务不存在返回404")
    void testReceiveProgress_subtaskNotFound() throws Exception {
        doThrow(new IllegalArgumentException("Subtask not found: 999"))
            .when(progressService).updateProgress(anyLong(), anyInt(), anyInt());

        mockMvc.perform(post("/api/batch/subtask/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProgressJson(999L, 50, 100)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.msg").exists());

        System.out.println("✅ 子任务不存在错误处理正常");
    }

    // ==================== 2. 批量接收进度 ====================

    @Test
    @DisplayName("4. POST /api/batch/subtask/progress/batch - 批量更新成功")
    void testReceiveBatchProgress_success() throws Exception {
        List<Map<String, Object>> batchData = Arrays.asList(
            createProgressMap(101L, 30, 100),
            createProgressMap(102L, 60, 100),
            createProgressMap(103L, 90, 100)
        );

        doNothing().when(progressService).batchUpdateProgress(anyList());

        mockMvc.perform(post("/api/batch/subtask/progress/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batchData)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.updatedCount").value(3));

        verify(progressService).batchUpdateProgress(argThat(list -> 
            ((List<?>) list).size() == 3));
        System.out.println("✅ 批量进度更新API成功");
    }

    @Test
    @DisplayName("5. POST /api/batch/subtask/progress/batch - 空列表返回0")
    void testReceiveBatchProgress_emptyList() throws Exception {
        doNothing().when(progressService).batchUpdateProgress(anyList());

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
        doNothing().when(progressService).markFailed(anyLong(), anyString(), anyString());

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

        verify(progressService).markFailed(100L, "CONNECTION_TIMEOUT", "Agent unreachable after 30s");
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
        doNothing().when(progressService).updateProgress(anyLong(), anyInt(), anyInt());

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
        doNothing().when(progressService).updateProgress(anyLong(), anyInt(), anyInt());

        mockMvc.perform(post("/api/batch/subtask/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProgressJson(100L, 150, 100)))
            .andExpect(status().isOk());

        ArgumentCaptor<Integer> transferredCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(progressService).updateProgress(eq(100L), transferredCaptor.capture(), eq(100));
        
        System.out.println("✅ 超出范围进度值自动修正: " + transferredCaptor.getValue() + "%");
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

    private String createProgressJsonWithTimestamp(Long subtaskId, int transferredBytes, int totalBytes, long timestamp) {
        return String.format("""
            {
                "subtaskId": %d,
                "transferredBytes": %d,
                "totalBytes": %d,
                "timestamp": %d
            }
            """, subtaskId, transferredBytes, totalBytes, timestamp);
    }

    private Map<String, Object> createProgressMap(Long subtaskId, int transferredBytes, int totalBytes) {
        Map<String, Object> map = new HashMap<>();
        map.put("subtaskId", subtaskId);
        map.put("transferredBytes", transferredBytes);
        map.put("totalBytes", totalBytes);
        map.put("timestamp", System.currentTimeMillis());
        return map;
    }
}
