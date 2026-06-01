package com.cq.panel.admin.server.service.batch.impl;

import com.cq.panel.admin.server.repository.domain.BatchSyncEvent;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.mapper.AgentRegistryMapper;
import com.cq.panel.admin.server.repository.mapper.BatchSyncEventMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferSubtaskMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper;
import com.cq.panel.admin.server.repository.service.impl.BatchTransferTaskServiceImpl;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTransferTaskCreateDTO;
import com.cq.panel.admin.server.service.batch.AgentDirectoryChecker;
import com.cq.panel.admin.server.service.batch.BatchConfigSerializer;
import com.cq.panel.admin.server.service.batch.CronExpressionValidator;
import com.cq.panel.admin.server.service.batch.WildcardConflictDetector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TDD测试：验证Admin端事务一致性
 * 核心要求（spec.md）：
 * 1. 配置更新 + 事件插入必须在同一事务中完成
 * 2. 如果事件插入失败，整个事务必须回滚，配置修改不会生效
 * 3. 保证不会出现"配置已更新但Proxy不知道"的不一致状态
 */
@DisplayName("Admin事务一致性 - 符合spec.md设计")
class BatchTransferTaskTransactionTest {

    private BatchTransferTaskServiceImpl service;
    private BatchTransferTaskMapper taskMapper;
    private BatchSyncEventMapper eventMapper;
    private BatchConfigSerializer configSerializer;

    @BeforeEach
    void setUp() {
        taskMapper = mock(BatchTransferTaskMapper.class);
        eventMapper = mock(BatchSyncEventMapper.class);
        configSerializer = mock(BatchConfigSerializer.class);
        WildcardConflictDetector conflictDetector = mock(WildcardConflictDetector.class);
        CronExpressionValidator cronValidator = mock(CronExpressionValidator.class);
        AgentRegistryMapper agentRegistryMapper = mock(AgentRegistryMapper.class);
        AgentDirectoryChecker directoryChecker = mock(AgentDirectoryChecker.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);

        service = new BatchTransferTaskServiceImpl(
            taskMapper,
            mock(BatchTransferSubtaskMapper.class),
            eventMapper,
            agentRegistryMapper,
            directoryChecker,
            conflictDetector,
            cronValidator,
            configSerializer,
            objectMapper
        );

        // 默认行为：无冲突
        when(conflictDetector.hasConflict(any(), any(), any(), any(), any(), any())).thenReturn(false);
        when(cronValidator.isValid(any())).thenReturn(true);
    }

    // ==================== Red Phase: 事务一致性测试 ====================

    @Test
    @DisplayName("1. [事务一致性] 创建任务时事件插入失败，任务操作应回滚")
    void testCreateTask_eventInsertFailure_shouldRollback() throws Exception {
        // Given: 任务插入成功，但事件插入失败
        when(taskMapper.insert(any())).thenAnswer(inv -> {
            BatchTransferTask task = inv.getArgument(0);
            task.setId(1L);
            return 1;
        });
        when(configSerializer.serialize(any())).thenReturn("{}");
        // 事件插入失败（模拟数据库异常）
        when(eventMapper.insertEvent(any())).thenThrow(new RuntimeException("数据库连接失败：无法插入事件"));

        BatchTransferTaskCreateDTO dto = createTestDTO();

        // When & Then: 应该抛出异常，表示事务回滚
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.createTask(dto, "admin");
        });

        assertTrue(exception.getMessage().contains("创建同步事件失败") || exception.getCause().getMessage().contains("数据库连接失败"));
        // 验证任务插入被调用（但在真实事务中会回滚）
        verify(taskMapper).insert(any(BatchTransferTask.class));
        verify(eventMapper).insertEvent(any(BatchSyncEvent.class));

        System.out.println("✅ 事务一致性验证: 事件插入失败时抛出异常，任务应回滚");
    }

    @Test
    @DisplayName("2. [事务一致性] 更新任务时事件插入失败，更新操作应回滚")
    void testUpdateTask_eventInsertFailure_shouldRollback() throws Exception {
        // Given: 现有任务
        BatchTransferTask existingTask = createExistingTask();
        when(taskMapper.selectById(1L)).thenReturn(existingTask);
        when(taskMapper.updateById(any())).thenReturn(1);
        when(configSerializer.serialize(any())).thenReturn("{}");
        // 事件插入失败
        when(eventMapper.insertEvent(any())).thenThrow(new RuntimeException("事件表写入失败"));

        BatchTransferTaskCreateDTO dto = createTestDTO();
        dto.setTaskName("更新后的名称");

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.updateTask(1L, dto, "admin");
        });

        assertTrue(exception.getMessage().contains("创建同步事件失败") || exception.getCause().getMessage().contains("事件表写入失败"));
        verify(taskMapper).updateById(any(BatchTransferTask.class));
        verify(eventMapper).insertEvent(any(BatchSyncEvent.class));

        System.out.println("✅ 事务一致性验证: 更新时事件插入失败抛出异常，更新应回滚");
    }

    @Test
    @DisplayName("3. [事务一致性] 启动任务时事件插入失败，状态更新应回滚")
    void testStartTask_eventInsertFailure_shouldRollback() throws Exception {
        BatchTransferTask task = createExistingTask();
        task.setStatus("READY");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.updateById(any())).thenReturn(1);
        when(configSerializer.serialize(any())).thenReturn("{}");
        when(eventMapper.insertEvent(any())).thenThrow(new RuntimeException("事件插入异常"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.startTask(1L);
        });

        assertTrue(exception.getMessage().contains("创建同步事件失败") || exception.getCause().getMessage().contains("事件插入异常"));
        verify(taskMapper).updateById(any(BatchTransferTask.class));
        verify(eventMapper).insertEvent(any(BatchSyncEvent.class));

        System.out.println("✅ 事务一致性验证: 启动时事件插入失败抛出异常，状态更新应回滚");
    }

    @Test
    @DisplayName("4. [事务一致性] 暂停任务时事件插入失败，状态更新应回滚")
    void testPauseTask_eventInsertFailure_shouldRollback() throws Exception {
        BatchTransferTask task = createExistingTask();
        task.setStatus("RUNNING");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.updateById(any())).thenReturn(1);
        when(configSerializer.serialize(any())).thenReturn("{}");
        when(eventMapper.insertEvent(any())).thenThrow(new RuntimeException("事件插入异常"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.pauseTask(1L);
        });

        assertTrue(exception.getMessage().contains("创建同步事件失败") || exception.getCause().getMessage().contains("事件插入异常"));
        verify(taskMapper).updateById(any(BatchTransferTask.class));
        verify(eventMapper).insertEvent(any(BatchSyncEvent.class));

        System.out.println("✅ 事务一致性验证: 暂停时事件插入失败抛出异常，状态更新应回滚");
    }

    @Test
    @DisplayName("5. [事务一致性] 恢复任务时事件插入失败，状态更新应回滚")
    void testResumeTask_eventInsertFailure_shouldRollback() throws Exception {
        BatchTransferTask task = createExistingTask();
        task.setStatus("PAUSED");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.updateById(any())).thenReturn(1);
        when(configSerializer.serialize(any())).thenReturn("{}");
        when(eventMapper.insertEvent(any())).thenThrow(new RuntimeException("事件插入异常"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.resumeTask(1L);
        });

        assertTrue(exception.getMessage().contains("创建同步事件失败") || exception.getCause().getMessage().contains("事件插入异常"));
        verify(taskMapper).updateById(any(BatchTransferTask.class));
        verify(eventMapper).insertEvent(any(BatchSyncEvent.class));

        System.out.println("✅ 事务一致性验证: 恢复时事件插入失败抛出异常，状态更新应回滚");
    }

    @Test
    @DisplayName("6. [事务一致性] 停止任务时事件插入失败，删除操作应回滚")
    void testStopTask_eventInsertFailure_shouldRollback() throws Exception {
        BatchTransferTask task = createExistingTask();
        task.setStatus("RUNNING");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.deleteById(1L)).thenReturn(1);
        when(configSerializer.serialize(any())).thenReturn("{}");
        when(eventMapper.insertEvent(any())).thenThrow(new RuntimeException("事件插入异常"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.stopTask(1L);
        });

        assertTrue(exception.getMessage().contains("创建同步事件失败") || exception.getCause().getMessage().contains("事件插入异常"));
        verify(taskMapper).deleteById(1L);
        verify(eventMapper).insertEvent(any(BatchSyncEvent.class));

        System.out.println("✅ 事务一致性验证: 停止时事件插入失败抛出异常，删除应回滚");
    }

    @Test
    @DisplayName("7. [事务一致性] 批量删除时每个任务都应生成对应事件")
    void testDeleteTasks_eachTaskShouldHaveEvent() throws Exception {
        BatchTransferTask task1 = createExistingTask();
        task1.setId(1L);
        task1.setSourceAgentId("agent-001");
        BatchTransferTask task2 = createExistingTask();
        task2.setId(2L);
        task2.setSourceAgentId("agent-002");

        when(taskMapper.selectById(1L)).thenReturn(task1);
        when(taskMapper.selectById(2L)).thenReturn(task2);
        when(taskMapper.deleteById(anyLong())).thenReturn(1);
        when(configSerializer.serialize(any())).thenReturn("{}");
        when(eventMapper.insertEvent(any())).thenReturn(1);

        service.deleteTasks(Arrays.asList(1L, 2L));

        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper, times(2)).insertEvent(eventCaptor.capture());

        List<BatchSyncEvent> events = eventCaptor.getAllValues();
        assertEquals(2, events.size());
        assertEquals("TASK_DELETED", events.get(0).getEventType());
        assertEquals(1L, events.get(0).getTaskId());
        assertEquals("agent-001", events.get(0).getSourceAgentId());
        assertEquals("TASK_DELETED", events.get(1).getEventType());
        assertEquals(2L, events.get(1).getTaskId());
        assertEquals("agent-002", events.get(1).getSourceAgentId());

        System.out.println("✅ 批量删除验证: 每个任务都生成了对应的事件");
    }

    @Test
    @DisplayName("8. [事务一致性] 批量删除时事件插入失败应回滚所有操作")
    void testDeleteTasks_eventInsertFailure_shouldRollbackAll() throws Exception {
        BatchTransferTask task1 = createExistingTask();
        task1.setId(1L);
        when(taskMapper.selectById(1L)).thenReturn(task1);
        when(taskMapper.deleteById(anyLong())).thenReturn(1);
        when(configSerializer.serialize(any())).thenReturn("{}");
        // 第一个事件插入就失败
        when(eventMapper.insertEvent(any())).thenThrow(new RuntimeException("事件插入失败"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.deleteTasks(Arrays.asList(1L));
        });

        assertTrue(
            exception.getMessage().contains("创建同步事件失败") ||
            (exception.getCause() != null && exception.getCause().getMessage().contains("事件插入失败"))
        );
        verify(taskMapper).deleteById(1L);
        verify(eventMapper).insertEvent(any(BatchSyncEvent.class));

        System.out.println("✅ 事务一致性验证: 批量删除时事件插入失败抛出异常，所有删除应回滚");
    }

    @Test
    @DisplayName("9. [spec.md] 停止任务应生成TASK_DELETED事件（而非直接物理删除）")
    void testStopTask_generatesTaskDeletedEvent() throws Exception {
        BatchTransferTask task = createExistingTask();
        task.setStatus("RUNNING");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.deleteById(1L)).thenReturn(1);
        when(configSerializer.serializeForAgent(any())).thenReturn("{\"taskId\":1}");
        when(eventMapper.insertEvent(any())).thenReturn(1);

        service.stopTask(1L);

        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).insertEvent(eventCaptor.capture());

        BatchSyncEvent event = eventCaptor.getValue();
        assertEquals("TASK_DELETED", event.getEventType());
        assertEquals(1L, event.getTaskId());
        assertNotNull(event.getPayload());

        System.out.println("✅ 停止任务生成TASK_DELETED事件验证通过");
    }

    @Test
    @DisplayName("10. [spec.md] 所有事件应设置expire_at为24小时后")
    void testAllEvents_haveExpireAt24Hours() throws Exception {
        when(taskMapper.insert(any())).thenAnswer(inv -> {
            BatchTransferTask task = inv.getArgument(0);
            task.setId(1L);
            return 1;
        });
        when(configSerializer.serialize(any())).thenReturn("{}");
        when(eventMapper.insertEvent(any())).thenReturn(1);

        BatchTransferTaskCreateDTO dto = createTestDTO();
        service.createTask(dto, "admin");

        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).insertEvent(eventCaptor.capture());

        BatchSyncEvent event = eventCaptor.getValue();
        assertNotNull(event.getExpireAt(), "事件必须设置过期时间");

        long now = System.currentTimeMillis();
        long expireTime = event.getExpireAt().getTime();
        long hours24 = 24 * 60 * 60 * 1000;
        assertTrue(expireTime >= now + hours24 - 60 * 1000, "过期时间应约为24小时后");
        assertTrue(expireTime <= now + hours24 + 60 * 1000, "过期时间应约为24小时后");

        System.out.println("✅ 事件过期时间验证: expireAt=" + event.getExpireAt());
    }

    // ==================== 辅助方法 ====================

    private BatchTransferTaskCreateDTO createTestDTO() {
        BatchTransferTaskCreateDTO dto = new BatchTransferTaskCreateDTO();
        dto.setTaskName("测试任务");
        dto.setSourceAgentId("agent-001");
        dto.setSourceAgentName("root@10.0.0.1:7777");
        dto.setSourceDir("/var/log/app");
        dto.setTargetDirs("/backup/logs");
        dto.setIncludePatterns(Arrays.asList("*.log"));
        dto.setTargetAgentIds(Arrays.asList("agent-002"));
        dto.setTargetAgentNames(Arrays.asList("root@10.0.0.2:7777"));
        return dto;
    }

    private BatchTransferTask createExistingTask() {
        BatchTransferTask task = new BatchTransferTask();
        task.setId(1L);
        task.setTaskName("现有任务");
        task.setSourceAgentId("agent-001");
        task.setSourceAgentName("root@10.0.0.1:7777");
        task.setSourceDir("/var/log/app");
        task.setTargetDirs("/backup/logs");
        task.setStatus("READY");
        task.setCreateBy("admin");
        task.setCreateTime(new Date());
        return task;
    }
}
