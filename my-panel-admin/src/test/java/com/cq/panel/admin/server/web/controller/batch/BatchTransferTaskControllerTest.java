package com.cq.panel.admin.server.web.controller.batch;

import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.service.IBatchTransferTaskService;
import com.cq.panel.admin.server.repository.service.IDirectoryCheckService;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTransferTaskCreateDTO;
import com.cq.panel.admin.server.service.batch.AgentDirectoryChecker;
import com.cq.panel.admin.server.web.converter.batch.BatchTransferTaskQueryConverter;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 批量传输任务 Controller 单元测试
 * 覆盖率目标：100%
 */
@DisplayName("批量传输任务 Controller - BatchTransferTaskController")
class BatchTransferTaskControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IBatchTransferTaskService batchTransferTaskService;

    @Mock
    private AgentDirectoryChecker directoryChecker;
    @Mock
    private BatchTransferTaskQueryConverter queryConverter;

    @Mock
    private IDirectoryCheckService directoryCheckService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // 创建控制器实例
        BatchTransferTaskController controller = new BatchTransferTaskController(batchTransferTaskService, directoryChecker, queryConverter, directoryCheckService);
        
        // 使用spy包装控制器以支持mock getUserId()
        controller = spy(controller);
        
        // Mock getUserId()/getUsername()返回固定值，避免SecurityUtils依赖
        doReturn(1L).when(controller).getUserId();
        doReturn("test-user").when(controller).getUsername();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .build();
    }

    // ==================== Task 3.1: CRUD接口测试 ====================

    @Test
    @DisplayName("1. POST /batch/task - 创建任务成功")
    void testCreate_success() throws Exception {
        when(batchTransferTaskService.createTask(any(BatchTransferTaskCreateDTO.class), anyString()))
            .thenReturn(100L);

        mockMvc.perform(post("/batch/task")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createValidJson("新测试任务")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").value(100));

        System.out.println("✅ 创建任务API成功");
    }

    @Test
    @DisplayName("2. POST /batch/task - 参数验证失败")
    void testCreate_validationError() throws Exception {
        doThrow(new IllegalArgumentException("任务名称不能为空"))
            .when(batchTransferTaskService).createTask(any(BatchTransferTaskCreateDTO.class), anyString());

        String invalidJson = "{\"taskName\":\"\"}";

        mockMvc.perform(post("/batch/task")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.msg").exists());

        System.out.println("✅ 参数验证错误正常返回");
    }

    @Test
    @DisplayName("3. POST /batch/task - 通配符冲突异常")
    void testCreate_conflictError() throws Exception {
        doThrow(new IllegalStateException("检测到通配符冲突!"))
            .when(batchTransferTaskService).createTask(any(BatchTransferTaskCreateDTO.class), anyString());

        mockMvc.perform(post("/batch/task")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createValidJson("冲突任务")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500));

        System.out.println("✅ 通配符冲突错误正常返回");
    }

    @Test
    @DisplayName("4. PUT /batch/task/{id} - 更新任务成功")
    void testUpdate_success() throws Exception {
        doNothing().when(batchTransferTaskService).updateTask(anyLong(), any(BatchTransferTaskCreateDTO.class), anyString());

        mockMvc.perform(put("/batch/task/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createValidJson("更新后的任务")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(batchTransferTaskService, times(1)).updateTask(eq(1L), any(BatchTransferTaskCreateDTO.class), anyString());
        System.out.println("✅ 更新任务API成功");
    }

    @Test
    @DisplayName("5. DELETE /batch/task/{ids} - 删除任务成功")
    void testDelete_success() throws Exception {
        doNothing().when(batchTransferTaskService).deleteTasks(anyList());

        mockMvc.perform(delete("/batch/task/1,2,3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(batchTransferTaskService, times(1)).deleteTasks(Arrays.asList(1L, 2L, 3L));
        System.out.println("✅ 删除任务API成功");
    }

    @Test
    @DisplayName("6. GET /batch/task/{id} - 查询任务详情成功")
    void testGetById_found() throws Exception {
        BatchTransferTask mockTask = createMockTask();
        when(batchTransferTaskService.getTaskById(1L)).thenReturn(mockTask);

        mockMvc.perform(get("/batch/task/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.taskName").value("现有任务"));

        System.out.println("✅ 查询任务详情API成功");
    }

    @Test
    @DisplayName("7. GET /batch/task/{id} - 任务不存在")
    void testGetById_notFound() throws Exception {
        when(batchTransferTaskService.getTaskById(999L)).thenReturn(null);

        mockMvc.perform(get("/batch/task/999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.msg").value("任务不存在: 999"));

        System.out.println("✅ 任务不存在时返回正确错误消息");
    }

    @Test
    @DisplayName("8. GET /batch/task/list - 列表查询成功")
    void testList_success() throws Exception {
        List<BatchTransferTask> mockList = Arrays.asList(
            createMockTask(),
            createMockTask()
        );
        when(queryConverter.toDomain(any())).thenReturn(new BatchTransferTask());
        when(batchTransferTaskService.getTaskList(any(), eq(1), eq(10))).thenReturn(mockList);

        mockMvc.perform(get("/batch/task/list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(2));

        System.out.println("✅ 任务列表查询API成功");
    }

    @Test
    @DisplayName("9. GET /batch/task/statistics - 统计信息查询成功")
    void testStatistics_success() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 10L);
        stats.put("running", 3L);
        stats.put("paused", 2L);
        stats.put("ready", 4L);
        stats.put("other", 1L);
        
        when(batchTransferTaskService.getStatistics()).thenReturn(stats);

        mockMvc.perform(get("/batch/task/statistics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(10))
            .andExpect(jsonPath("$.data.running").value(3));

        System.out.println("📊 统计信息查询API成功");
    }

    // ==================== Task 3.2: 状态管理接口测试 ====================

    @Test
    @DisplayName("10. PUT /batch/task/{id}/start - 启动任务成功")
    void testStart_success() throws Exception {
        doNothing().when(batchTransferTaskService).startTask(1L);

        mockMvc.perform(put("/batch/task/1/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(batchTransferTaskService, times(1)).startTask(1L);
        System.out.println("✅ 启动任务API成功");
    }

    @Test
    @DisplayName("11. PUT /batch/task/{id}/pause - 暂停任务成功")
    void testPause_success() throws Exception {
        doNothing().when(batchTransferTaskService).pauseTask(1L);

        mockMvc.perform(put("/batch/task/1/pause"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(batchTransferTaskService, times(1)).pauseTask(1L);
        System.out.println("✅ 暂停任务API成功");
    }

    @Test
    @DisplayName("12. PUT /batch/task/{id}/resume - 恢复任务成功")
    void testResume_success() throws Exception {
        doNothing().when(batchTransferTaskService).resumeTask(1L);

        mockMvc.perform(put("/batch/task/1/resume"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(batchTransferTaskService, times(1)).resumeTask(1L);
        System.out.println("✅ 恢复任务API成功");
    }

    @Test
    @DisplayName("13. DELETE /batch/task/{id}/stop - 停止任务成功")
    void testStop_success() throws Exception {
        doNothing().when(batchTransferTaskService).stopTask(1L);

        mockMvc.perform(delete("/batch/task/1/stop"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(batchTransferTaskService, times(1)).stopTask(1L);
        System.out.println("✅ 停止任务API成功");
    }

    @Test
    @DisplayName("14. 启动已运行任务 - 异常处理")
    void testStart_alreadyRunning_error() throws Exception {
        doThrow(new IllegalStateException("任务已在运行中: 1"))
            .when(batchTransferTaskService).startTask(1L);

        mockMvc.perform(put("/batch/task/1/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.msg").value("任务已在运行中: 1"));

        System.out.println("✅ 启动运行中任务的异常处理正常");
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("15. 空ID列表删除 - 验证错误 (404)")
    void testDelete_emptyIds_error() throws Exception {
        mockMvc.perform(delete("/batch/task/{ids}", ""))
            .andExpect(status().isNotFound());

        System.out.println("✅ 空ID列表验证正常 (404)");
    }

    @Test
    @DisplayName("16. 无效ID格式 - 验证错误")
    void testDelete_invalidIdFormat_error() throws Exception {
        doThrow(new IllegalArgumentException("无效的任务ID格式: abc"))
            .when(batchTransferTaskService).deleteTasks(anyList());

        mockMvc.perform(delete("/batch/task/abc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500));

        System.out.println("✅ 无效ID格式验证正常");
    }

    @Test
    @DisplayName("17. 条件过滤查询 - 按状态筛选")
    void testList_withStatusFilter() throws Exception {
        List<BatchTransferTask> runningTasks = List.of(createMockTask());
        when(queryConverter.toDomain(any())).thenReturn(new BatchTransferTask());
        when(batchTransferTaskService.getTaskList(any(), eq(1), eq(10))).thenReturn(runningTasks);

        mockMvc.perform(get("/batch/task/list?status=RUNNING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.length()").value(1));

        System.out.println("✅ 状态过滤查询正常");
    }

    // ==================== 目录检查接口测试 ====================

    @Test
    @DisplayName("18. GET /batch/task/check-dir - 目录存在")
    void testCheckDirectory_exists() throws Exception {
        when(directoryChecker.checkDirectoryExists("agent-001", "/var/log/app"))
            .thenReturn(true);

        mockMvc.perform(get("/batch/task/check-dir")
                .param("agentId", "agent-001")
                .param("dirPath", "/var/log/app"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.exists").value(true))
            .andExpect(jsonPath("$.data.status").value("exists"))
            .andExpect(jsonPath("$.data.message").value("目录存在"));

        System.out.println("✅ 目录存在检查正常");
    }

    @Test
    @DisplayName("19. GET /batch/task/check-dir - 目录不存在")
    void testCheckDirectory_notExists() throws Exception {
        when(directoryChecker.checkDirectoryExists("agent-001", "/nonexistent/path"))
            .thenReturn(false);

        mockMvc.perform(get("/batch/task/check-dir")
                .param("agentId", "agent-001")
                .param("dirPath", "/nonexistent/path"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.exists").value(false))
            .andExpect(jsonPath("$.data.status").value("not_exists"))
            .andExpect(jsonPath("$.data.message").value("目录不存在"));

        System.out.println("✅ 目录不存在检查正常");
    }

    @Test
    @DisplayName("20. GET /batch/task/check-dir - 检查失败(未知)")
    void testCheckDirectory_unknown() throws Exception {
        when(directoryChecker.checkDirectoryExists("agent-001", "/var/log/app"))
            .thenReturn(null);

        mockMvc.perform(get("/batch/task/check-dir")
                .param("agentId", "agent-001")
                .param("dirPath", "/var/log/app"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.exists").value(nullValue()))
            .andExpect(jsonPath("$.data.status").value("unknown"))
            .andExpect(jsonPath("$.data.message").value("无法检查（Agent离线或网络异常）"));

        System.out.println("✅ 目录检查未知状态正常");
    }

    // ==================== 辅助方法 ====================

    private String createValidJson(String taskName) {
        return String.format("""
            {
                "taskName": "%s",
                "sourceAgentId": "agent-001",
                "sourceAgentName": "root@10.240.85.177:7777",
                "sourceDir": "/data/test",
                "targetDirs": "/backup/test",
                "includePatterns": ["*.txt"],
                "targetAgentIds": ["agent-002"],
                "targetAgentNames": ["root@node2:7777"]
            }
            """, taskName);
    }

    private BatchTransferTask createMockTask() {
        BatchTransferTask task = new BatchTransferTask();
        task.setId(1L);
        task.setTaskName("现有任务");
        task.setSourceAgentId("agent-001");
        task.setSourceAgentName("root@10.240.85.177:7777");
        task.setSourceDir("/var/log/app");
        task.setStatus("READY");
        return task;
    }
}
