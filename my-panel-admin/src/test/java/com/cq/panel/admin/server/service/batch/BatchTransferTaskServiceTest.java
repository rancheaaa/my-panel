package com.cq.panel.admin.server.service.batch;

import com.cq.panel.admin.server.repository.domain.BatchSyncEvent;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.mapper.BatchSyncEventMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferSubtaskMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper;
import com.cq.panel.admin.server.service.batch.dto.BatchTransferTaskDTO;
import com.cq.panel.admin.server.service.batch.impl.BatchTransferTaskServiceImpl;
import com.cq.panel.admin.server.service.batch.util.BatchConfigSerializer;
import com.cq.panel.admin.server.service.batch.util.CronExpressionValidator;
import com.cq.panel.admin.server.service.batch.util.WildcardConflictDetector;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 批量传输任务服务 单元测试（Mockito模式）
 * 覆盖率目标：100%
 */
@DisplayName("批量传输任务服务 - BatchTransferTaskService")
class BatchTransferTaskServiceTest {

    @Mock
    private BatchTransferTaskMapper taskMapper;

    @Mock
    private BatchTransferSubtaskMapper subtaskMapper;

    @Mock
    private BatchSyncEventMapper eventMapper;

    private WildcardConflictDetector conflictDetector;
    private CronExpressionValidator cronValidator;

    @Mock
    private BatchConfigSerializer configSerializer;
    
    private IBatchTransferTaskService taskService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        conflictDetector = new WildcardConflictDetector();
        cronValidator = new CronExpressionValidator();

        taskService = new BatchTransferTaskServiceImpl(
            taskMapper,
            subtaskMapper,
            eventMapper,
            conflictDetector,
            cronValidator,
            configSerializer
        );
    }

    // ==================== Task 2.1: 创建任务测试 ====================

    @Test
    @DisplayName("1. 创建任务成功 - 返回任务ID")
    void testCreateTask_success_returnsId() {
        BatchTransferTaskDTO dto = createValidDTO("新测试任务");
        
        when(taskMapper.insert(any(BatchTransferTask.class))).thenAnswer(invocation -> {
            BatchTransferTask task = invocation.getArgument(0);
            task.setId(100L);
            return 1;
        });

        Long taskId = taskService.createTask(dto, "test-user");

        assertNotNull(taskId);
        assertEquals(100L, taskId);

        verify(taskMapper).insert(any(BatchTransferTask.class));
        verify(eventMapper).insertEvent(any(BatchSyncEvent.class));

        System.out.println("✅ 创建任务成功! ID=" + taskId);
    }

    @Test
    @DisplayName("2. 通配符冲突 - 抛出异常")
    void testCreateTask_withConflict_throwsException() {
        BatchTransferTask existing = createExistingTask();
        existing.setSourceAgentId("agent-003");
        when(taskMapper.selectList(any())).thenReturn(Arrays.asList(existing));
        when(configSerializer.deserialize(anyString(), eq(List.class)))
            .thenReturn(Arrays.asList("*.log"))
            .thenReturn(Arrays.asList("debug*"));

        BatchTransferTaskDTO dto = createValidDTO("冲突任务");
        dto.setSourceDir("/var/log/app");
        dto.setIncludePatterns(Arrays.asList("*.log"));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> taskService.createTask(dto, "test-user")
        );

        assertTrue(exception.getMessage().contains("通配符冲突"));
 
        verify(taskMapper, never()).insert(any());
        System.out.println("✅ 冲突检测正常: " + exception.getMessage());
    }

    @Test
    @DisplayName("3. 事务性验证 - 任务和事件同时插入")
    void testCreateTask_transactional_bothInserted() {
        when(taskMapper.insert(any(BatchTransferTask.class))).thenAnswer(invocation -> {
            BatchTransferTask task = invocation.getArgument(0);
            task.setId(200L);
            return 1;
        });
        when(eventMapper.insertEvent(any())).thenReturn(1);

        BatchTransferTaskDTO dto = createValidDTO("事务测试任务");
        Long taskId = taskService.createTask(dto, "admin");

        ArgumentCaptor<BatchTransferTask> taskCaptor = ArgumentCaptor.forClass(BatchTransferTask.class);
        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        
        verify(taskMapper).insert(taskCaptor.capture());
        verify(eventMapper).insertEvent(eventCaptor.capture());

        assertEquals(200L, taskId);
        assertEquals("TASK_CREATED", eventCaptor.getValue().getEventType());
        assertEquals(taskId, eventCaptor.getValue().getTaskId());

        System.out.println("✅ 事务性验证通过: 任务和事件同时创建");
    }

    @Test
    @DisplayName("4. 缺少必填字段 - 验证错误")
    void testCreateTask_missingRequiredField_validationError() {
        BatchTransferTaskDTO dto = new BatchTransferTaskDTO();
        dto.setTaskName(null);

        assertThrows(IllegalArgumentException.class, () -> 
            taskService.createTask(dto, "test-user")
        );

        System.out.println("✅ 必填字段验证正常");
    }

    @Test
    @DisplayName("5. 空任务名 - 验证错误")
    void testCreateTask_emptyName_validationError() {
        BatchTransferTaskDTO dto = createValidDTO("");
        dto.setTaskName("");

        assertThrows(IllegalArgumentException.class, () -> 
            taskService.createTask(dto, "test-user")
        );

        System.out.println("✅ 空名称验证正常");
    }

    @Test
    @DisplayName("6. Payload包含完整配置信息")
    void testCreateTask_eventPayloadContainsFullConfig() {
        when(taskMapper.insert(any())).thenReturn(1);
        when(eventMapper.insertEvent(any())).thenReturn(1);
        when(configSerializer.serializeForAgent(any())).thenReturn("{\"taskId\":1,\"taskName\":\"Payload测试任务\",\"targetAgents\":[{\"agentId\":\"agent-003\",\"agentName\":\"root@node3:7777\"}]}");

        BatchTransferTaskDTO dto = createValidDTO("Payload测试任务");
        dto.setIncludePatterns(Arrays.asList("*.txt", "*.log"));
        dto.setTargetAgentIds(Arrays.asList("agent-003"));
        dto.setTargetAgentNames(Arrays.asList("root@node3:7777"));

        taskService.createTask(dto, "test-user");

        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).insertEvent(eventCaptor.capture());

        String payload = eventCaptor.getValue().getPayload();
        assertNotNull(payload);
        assertTrue(payload.contains("Payload测试任务"));
        assertTrue(payload.contains("agent-003"));
        assertTrue(payload.contains("root@node3:7777"));

        System.out.println("✅ Payload完整性验证通过");
    }

    @Test
    @DisplayName("7. 默认状态为READY")
    void testCreateTask_defaultStatusReady() {
        when(taskMapper.insert(any())).thenReturn(1);

        BatchTransferTaskDTO dto = createValidDTO("默认状态测试");
        taskService.createTask(dto, "test-user");

        ArgumentCaptor<BatchTransferTask> taskCaptor = ArgumentCaptor.forClass(BatchTransferTask.class);
        verify(taskMapper).insert(taskCaptor.capture());

        assertEquals("READY", taskCaptor.getValue().getStatus());

        System.out.println("✅ 默认状态验证: READY");
    }

    @Test
    @DisplayName("5. Agent名称持久化")
    void testCreateTask_agentNamesPersisted() {
        when(taskMapper.insert(any())).thenReturn(1);
        when(configSerializer.serializeForAgent(any())).thenReturn("{}");
        when(configSerializer.serialize(any(List.class))).thenReturn("[\"root@node1:7777\",\"root@node2:7777\"]");

        BatchTransferTaskDTO dto = createValidDTO("Agent名称测试");
        dto.setSourceAgentName("root@192.168.1.100:8888");
        dto.setTargetAgentNames(Arrays.asList("root@node1:7777", "root@node2:7777"));

        taskService.createTask(dto, "test-user");

        ArgumentCaptor<BatchTransferTask> taskCaptor = ArgumentCaptor.forClass(BatchTransferTask.class);
        verify(taskMapper).insert(taskCaptor.capture());

        BatchTransferTask saved = taskCaptor.getValue();
        assertEquals("root@192.168.1.100:8888", saved.getSourceAgentName());
        assertNotNull(saved.getTargetAgentNames());
        assertTrue(saved.getTargetAgentNames().contains("root@node1:7777"));
        assertTrue(saved.getTargetAgentNames().contains("root@node2:7777"));

        System.out.println("✅ Agent名称持久化验证通过");
    }

    // ==================== Task 2.2: 更新任务测试 ====================

    @Test
    @DisplayName("9. 更新任务成功")
    void testUpdateTask_success() {
        when(taskMapper.selectById(anyLong())).thenReturn(createExistingTask());
        when(taskMapper.updateById(any())).thenReturn(1);

        BatchTransferTaskDTO dto = createValidDTO("更新后的任务名");
        taskService.updateTask(1L, dto, "test-user");

        ArgumentCaptor<BatchTransferTask> captor = ArgumentCaptor.forClass(BatchTransferTask.class);
        verify(taskMapper).updateById(captor.capture());

        assertEquals("更新后的任务名", captor.getValue().getTaskName());
        System.out.println("✅ 更新任务成功");
    }

    @Test
    @DisplayName("10. 更新后检测到新冲突")
    void testUpdateTask_newConflict_detected() {
        BatchTransferTask existing = createExistingTask();
        when(taskMapper.selectById(anyLong())).thenReturn(existing);
        BatchTransferTask conflict1 = createExistingTaskWithId(2L);
        conflict1.setSourceAgentId("agent-003");
        BatchTransferTask conflict2 = createExistingTaskWithId(3L);
        conflict2.setSourceAgentId("agent-003");
        when(taskMapper.selectList(any())).thenReturn(Arrays.asList(conflict1, conflict2));
        when(configSerializer.deserialize(anyString(), eq(List.class)))
            .thenReturn(Arrays.asList("*.log"))
            .thenReturn(Arrays.asList("debug*"));

        BatchTransferTaskDTO dto = createValidDTO("冲突更新任务");
        dto.setSourceDir("/var/log/app");
        dto.setIncludePatterns(Arrays.asList("*.log"));

        assertThrows(IllegalStateException.class, () ->
            taskService.updateTask(1L, dto, "test-user")
        );
        
        System.out.println("✅ 更新冲突检测正常");
    }

    @Test
    @DisplayName("11. 更新生成TASK_UPDATED事件")
    void testUpdateTask_eventTypeTASK_UPDATED() {
        when(taskMapper.selectById(anyLong())).thenReturn(createExistingTask());
        when(taskMapper.updateById(any())).thenReturn(1);

        BatchTransferTaskDTO dto = createValidDTO("事件测试任务");
        taskService.updateTask(1L, dto, "test-user");

        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).insertEvent(eventCaptor.capture());

        assertEquals("TASK_UPDATED", eventCaptor.getValue().getEventType());
        System.out.println("✅ 事件类型正确: TASK_UPDATED");
    }

    // ==================== Task 2.3: 状态管理测试 ====================

    @Test
    @DisplayName("12. READY → RUNNING 启动任务")
    void testStart_READY_to_RUNNING() {
        BatchTransferTask readyTask = createExistingTask();
        readyTask.setStatus("READY");
        when(taskMapper.selectById(anyLong())).thenReturn(readyTask);
        when(taskMapper.updateById(any())).thenReturn(1);

        taskService.startTask(1L);

        ArgumentCaptor<BatchTransferTask> captor = ArgumentCaptor.forClass(BatchTransferTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals("RUNNING", captor.getValue().getStatus());
        assertNotNull(captor.getValue().getStartedAt());
        
        System.out.println("✅ 状态转换: READY → RUNNING");
    }

    @Test
    @DisplayName("13. RUNNING → PAUSED 暂停任务")
    void testPause_RUNNING_to_PAUSED() {
        BatchTransferTask runningTask = createExistingTask();
        runningTask.setStatus("RUNNING");
        when(taskMapper.selectById(anyLong())).thenReturn(runningTask);
        when(taskMapper.updateById(any())).thenReturn(1);

        taskService.pauseTask(1L);

        ArgumentCaptor<BatchTransferTask> captor = ArgumentCaptor.forClass(BatchTransferTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals("PAUSED", captor.getValue().getStatus());
        
        System.out.println("✅ 状态转换: RUNNING → PAUSED");
    }

    @Test
    @DisplayName("14. PAUSED → RUNNING 恢复任务")
    void testResume_PAUSED_to_RUNNING() {
        BatchTransferTask pausedTask = createExistingTask();
        pausedTask.setStatus("PAUSED");
        when(taskMapper.selectById(anyLong())).thenReturn(pausedTask);
        when(taskMapper.updateById(any())).thenReturn(1);

        taskService.resumeTask(1L);

        ArgumentCaptor<BatchTransferTask> captor = ArgumentCaptor.forClass(BatchTransferTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals("RUNNING", captor.getValue().getStatus());
        
        System.out.println("✅ 状态转换: PAUSED → RUNNING");
    }

    @Test
    @DisplayName("15. RUNNING/PAUSED → deleted=1 停止任务(逻辑删除)")
    void testStop_to_deleted() {
        for (String status : Arrays.asList("RUNNING", "PAUSED")) {
            BatchTransferTask task = createExistingTask();
            task.setStatus(status);
            when(taskMapper.selectById(anyLong())).thenReturn(task);
            when(taskMapper.deleteById(anyLong())).thenReturn(1);
            when(configSerializer.serializeForAgent(any())).thenReturn("{\"taskId\":1}");

            taskService.stopTask(1L);

            verify(taskMapper).deleteById(1L);

            reset(taskMapper);
        }

        System.out.println("✅ 停止任务: RUNNING/PAUSED → deleted=1 (逻辑删除)");
    }

    @Test
    @DisplayName("16. 已运行任务重复启动 - 异常")
    void testStart_ALREADY_RUNNING_exception() {
        BatchTransferTask runningTask = createExistingTask();
        runningTask.setStatus("RUNNING");
        when(taskMapper.selectById(anyLong())).thenReturn(runningTask);

        assertThrows(IllegalStateException.class, () -> taskService.startTask(1L));
        System.out.println("✅ 重复启动异常正常");
    }

    @Test
    @DisplayName("17. 非RUNNING状态暂停 - 异常")
    void testPause_READY_state_invalid() {
        BatchTransferTask readyTask = createExistingTask();
        readyTask.setStatus("READY");
        when(taskMapper.selectById(anyLong())).thenReturn(readyTask);

        assertThrows(IllegalStateException.class, () -> taskService.pauseTask(1L));
        System.out.println("✅ 无效状态暂停异常正常");
    }

    @Test
    @DisplayName("18. 删除任务 - 逻辑删除 + TASK_DELETED事件")
    void testDelete_logicalDelete_only() {
        when(taskMapper.selectById(anyLong())).thenReturn(createExistingTask());
        when(taskMapper.deleteById(anyLong())).thenReturn(1);
        when(eventMapper.insertEvent(any())).thenReturn(1);
        when(configSerializer.serializeForAgent(any())).thenReturn("{\"taskId\":1}");

        taskService.deleteTasks(Arrays.asList(1L));

        verify(taskMapper).deleteById(1L);
        
        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).insertEvent(eventCaptor.capture());
        assertEquals("TASK_DELETED", eventCaptor.getValue().getEventType());
        
        System.out.println("✅ 逻辑删除+事件生成正常");
    }

    // ==================== Task 2.4: 查询服务测试 ====================

    @Test
    @DisplayName("19. 根据ID查询任务")
    void testGetById_found() {
        BatchTransferTask expected = createExistingTask();
        when(taskMapper.selectById(1L)).thenReturn(expected);

        BatchTransferTask result = taskService.getTaskById(1L);

        assertNotNull(result);
        assertEquals("现有任务-日志备份", result.getTaskName());
        System.out.println("✅ ID查询成功");
    }

    @Test
    @DisplayName("20. 统计信息查询")
    void testGetStatistics_success() {
        List<BatchTransferTask> mockList = Arrays.asList(
            createTaskWithStatus("RUNNING"),
            createTaskWithStatus("PAUSED"),
            createTaskWithStatus("READY"),
            createTaskWithStatus("READY"),
            createTaskWithStatus("COMPLETED")
        );
        when(taskMapper.selectList(any())).thenReturn(mockList);

        Map<String, Object> stats = taskService.getStatistics();

        assertNotNull(stats);
        assertEquals(5L, stats.get("total"));
        assertEquals(1L, stats.get("running"));
        assertEquals(1L, stats.get("paused"));
        assertEquals(2L, stats.get("ready"));
        assertEquals(1L, stats.get("other"));
        
        System.out.println("📊 统计信息: total=5, running=1, paused=1, ready=2, other=1");
    }

    // ==================== 辅助方法 ====================

    private BatchTransferTaskDTO createValidDTO(String taskName) {
        BatchTransferTaskDTO dto = new BatchTransferTaskDTO();
        dto.setTaskName(taskName);
        dto.setTaskDescription("测试任务描述");
        dto.setSourceAgentId("agent-003");
        dto.setSourceAgentName("root@10.240.85.179:7777");
        dto.setSourceDir("/data/test");
        dto.setTargetDirs("/backup/test");
        dto.setIncludePatterns(Arrays.asList("*.txt"));
        dto.setExcludePatterns(Arrays.asList("temp*"));
        dto.setMaxScanFiles(5000);
        dto.setTargetAgentIds(Arrays.asList("agent-004"));
        dto.setTargetAgentNames(Arrays.asList("root@node4:7777"));
        dto.setRetryEnabled(1);
        dto.setRetryMaxDays(5);
        dto.setRetryIntervalMin(15);
        dto.setMaxRetryCount(8);
        dto.setRetryBackoffType("LINEAR");
        dto.setPostTransferAction("NONE");
        dto.setPreserveDirStructure(1);
        dto.setTransferMode("ONE_TO_ONE");
        dto.setRoutingStrategy("ROUND_ROBIN");
        dto.setRemark("测试备注");
        return dto;
    }

    private BatchTransferTask createExistingTask() {
        BatchTransferTask task = new BatchTransferTask();
        task.setId(1L);
        task.setTaskName("现有任务-日志备份");
        task.setSourceAgentId("agent-001");
        task.setSourceAgentName("root@10.240.85.177:7777");
        task.setSourceDir("/var/log/app");
        task.setTargetDirs("/backup/logs");
        task.setIncludePatterns("[\"*.log\"]");
        task.setExcludePatterns("[\"debug*\"]");
        task.setTargetAgentIds("[\"agent-002\"]");
        task.setTargetAgentNames("[\"root@node2:7777\"]");
        task.setStatus("READY");
        return task;
    }
    
    private BatchTransferTask createExistingTaskWithId(Long id) {
        BatchTransferTask task = createExistingTask();
        task.setId(id);
        return task;
    }

    private BatchTransferTask createTaskWithStatus(String status) {
        BatchTransferTask task = new BatchTransferTask();
        task.setStatus(status);
        return task;
    }
}
