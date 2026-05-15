package com.cq.panel.admin.server.service.batch.impl;

import com.cq.panel.admin.server.repository.domain.BatchSyncEvent;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.mapper.AgentRegistryMapper;
import com.cq.panel.admin.server.repository.mapper.BatchSyncEventMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferSubtaskMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper;
import com.cq.panel.admin.server.service.batch.dto.BatchTransferTaskDTO;
import com.cq.panel.admin.server.service.batch.util.AgentDirectoryChecker;
import com.cq.panel.admin.server.service.batch.util.BatchConfigSerializer;
import com.cq.panel.admin.server.service.batch.util.CronExpressionValidator;
import com.cq.panel.admin.server.service.batch.util.WildcardConflictDetector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BatchSyncEvent符合spec.md的单元测试
 * 验证事件队列机制：expire_at设置、事务一致性、事件类型正确性
 */
@DisplayName("批量同步事件 - 符合spec.md设计")
class BatchSyncEventSpecTest {

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
            taskMapper, mock(BatchTransferSubtaskMapper.class),
            eventMapper, agentRegistryMapper, directoryChecker, conflictDetector, cronValidator, configSerializer,
            objectMapper
        );
    }

    @Test
    @DisplayName("1. 创建任务时 - 事件应包含expire_at（24小时后过期）")
    void testCreateTask_eventHasExpireAt() throws Exception {
        BatchTransferTaskDTO dto = createTestDTO();
        when(taskMapper.insert(any())).thenAnswer(inv -> {
            BatchTransferTask task = inv.getArgument(0);
            task.setId(1L);
            return 1;
        });
        when(configSerializer.serialize(any())).thenReturn("{}");

        service.createTask(dto, "admin");

        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).insertEvent(eventCaptor.capture());

        BatchSyncEvent event = eventCaptor.getValue();
        assertNotNull(event.getExpireAt(), "事件应设置过期时间");

        // 验证expire_at在23-25小时之间（允许一定误差）
        long now = System.currentTimeMillis();
        long expireTime = event.getExpireAt().getTime();
        long hours24 = 24 * 60 * 60 * 1000;
        assertTrue(expireTime > now + hours24 - 60 * 1000, "过期时间应大于23小时");
        assertTrue(expireTime < now + hours24 + 60 * 1000, "过期时间应小于25小时");

        System.out.println("✅ 创建任务事件: expireAt=" + event.getExpireAt());
    }

    @Test
    @DisplayName("2. 创建任务时 - 事件类型应为TASK_CREATED")
    void testCreateTask_eventType() throws Exception {
        BatchTransferTaskDTO dto = createTestDTO();
        when(taskMapper.insert(any())).thenAnswer(inv -> {
            BatchTransferTask task = inv.getArgument(0);
            task.setId(1L);
            return 1;
        });
        when(configSerializer.serialize(any())).thenReturn("{}");

        service.createTask(dto, "admin");

        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).insertEvent(eventCaptor.capture());

        BatchSyncEvent event = eventCaptor.getValue();
        assertEquals("TASK_CREATED", event.getEventType(), "事件类型应为TASK_CREATED");
        assertEquals(1L, event.getTaskId(), "taskId应正确");
        assertEquals("agent-001", event.getSourceAgentId(), "sourceAgentId应正确");
        assertEquals("PENDING", event.getStatus(), "初始状态应为PENDING");
        assertEquals(0, event.getRetryCount(), "初始重试次数应为0");

        System.out.println("✅ 事件类型验证: TASK_CREATED, PENDING, retry=0");
    }

    @Test
    @DisplayName("3. 更新任务时 - 事件类型应为TASK_UPDATED")
    void testUpdateTask_eventType() throws Exception {
        BatchTransferTaskDTO dto = createTestDTO();
        BatchTransferTask existingTask = new BatchTransferTask();
        existingTask.setId(1L);
        existingTask.setStatus("READY");
        existingTask.setSourceAgentId("agent-001");
        existingTask.setCreateBy("admin");
        existingTask.setCreateTime(new Date());

        when(taskMapper.selectById(1L)).thenReturn(existingTask);
        when(configSerializer.serialize(any())).thenReturn("{}");

        service.updateTask(1L, dto, "admin");

        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).insertEvent(eventCaptor.capture());

        BatchSyncEvent event = eventCaptor.getValue();
        assertEquals("TASK_UPDATED", event.getEventType(), "事件类型应为TASK_UPDATED");
        assertNotNull(event.getExpireAt(), "更新事件也应设置过期时间");

        System.out.println("✅ 更新任务事件: TASK_UPDATED");
    }

    @Test
    @DisplayName("4. 启动任务时 - 事件类型应为TASK_STATUS_CHANGED")
    void testStartTask_eventType() throws Exception {
        BatchTransferTask task = new BatchTransferTask();
        task.setId(1L);
        task.setStatus("READY");
        task.setSourceAgentId("agent-001");

        when(taskMapper.selectById(1L)).thenReturn(task);
        when(configSerializer.serialize(any())).thenReturn("{}");

        service.startTask(1L);

        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).insertEvent(eventCaptor.capture());

        BatchSyncEvent event = eventCaptor.getValue();
        assertEquals("TASK_STATUS_CHANGED", event.getEventType(), "事件类型应为TASK_STATUS_CHANGED");

        System.out.println("✅ 启动任务事件: TASK_STATUS_CHANGED");
    }

    @Test
    @DisplayName("5. 删除任务时 - 事件类型应为TASK_DELETED")
    void testDeleteTask_eventType() throws Exception {
        BatchTransferTask task = new BatchTransferTask();
        task.setId(1L);
        task.setStatus("RUNNING");
        task.setSourceAgentId("agent-001");

        when(taskMapper.selectById(1L)).thenReturn(task);
        when(configSerializer.serialize(any())).thenReturn("{}");

        service.stopTask(1L);

        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).insertEvent(eventCaptor.capture());

        BatchSyncEvent event = eventCaptor.getValue();
        assertEquals("TASK_DELETED", event.getEventType(), "事件类型应为TASK_DELETED");

        System.out.println("✅ 删除任务事件: TASK_DELETED");
    }

    private BatchTransferTaskDTO createTestDTO() {
        BatchTransferTaskDTO dto = new BatchTransferTaskDTO();
        dto.setTaskName("测试任务");
        dto.setSourceAgentId("agent-001");
        dto.setSourceAgentName("root@10.0.0.1:7777");
        dto.setSourceDir("/var/log/app");
        dto.setTargetDirs("/backup/logs");
        dto.setIncludePatterns(List.of("*.log"));
        dto.setTargetAgentIds(List.of("agent-002"));
        dto.setTargetAgentNames(List.of("root@10.0.0.2:7777"));
        return dto;
    }
}
