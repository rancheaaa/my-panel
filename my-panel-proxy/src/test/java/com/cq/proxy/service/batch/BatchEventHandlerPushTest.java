package com.cq.proxy.service.batch;

import com.cq.proxy.repository.entity.BatchSyncEvent;
import com.cq.proxy.repository.mapper.BatchSyncEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BatchEventHandler 推送功能测试
 * 验证事件处理器调用AgentPushService推送配置到Agent
 * 符合spec.md设计要求
 */
class BatchEventHandlerPushTest {

    @Mock
    private BatchSyncEventMapper eventMapper;

    @Mock
    private AgentPushService agentPushService;

    private ObjectMapper objectMapper;

    @InjectMocks
    private BatchEventHandler handler;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        handler = new BatchEventHandler(eventMapper, objectMapper, agentPushService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ==================== pushConfigToAgent - 成功场景 ====================

    @Test
    @DisplayName("1. TASK_CREATED - 推送配置成功并标记COMPLETED")
    void testHandleEvent_taskCreated_pushSuccess() {
        BatchSyncEvent event = createEvent("TASK_CREATED", 1L, "{\"taskId\":1,\"taskName\":\"test\"}");

        when(agentPushService.pushConfigToAgent(any(), any())).thenReturn(true);

        handler.handleEvent(event);

        verify(agentPushService).pushConfigToAgent(eq("agent-001"), any());
        verify(eventMapper).updateStatusToCompleted(event.getId());
    }

    @Test
    @DisplayName("2. TASK_UPDATED - 推送配置成功并标记COMPLETED")
    void testHandleEvent_taskUpdated_pushSuccess() {
        BatchSyncEvent event = createEvent("TASK_UPDATED", 1L, "{\"taskId\":1,\"taskName\":\"updated\"}");

        when(agentPushService.pushConfigToAgent(any(), any())).thenReturn(true);

        handler.handleEvent(event);

        verify(agentPushService).pushConfigToAgent(eq("agent-001"), any());
        verify(eventMapper).updateStatusToCompleted(event.getId());
    }

    @Test
    @DisplayName("3. TASK_STATUS_CHANGED - 推送配置成功并标记COMPLETED")
    void testHandleEvent_taskStatusChanged_pushSuccess() {
        BatchSyncEvent event = createEvent("TASK_STATUS_CHANGED", 1L, "{\"taskId\":1,\"status\":\"RUNNING\"}");

        when(agentPushService.pushConfigToAgent(any(), any())).thenReturn(true);

        handler.handleEvent(event);

        verify(agentPushService).pushConfigToAgent(eq("agent-001"), any());
        verify(eventMapper).updateStatusToCompleted(event.getId());
    }

    @Test
    @DisplayName("4. TASK_DELETED - 推送删除指令成功并标记COMPLETED")
    void testHandleEvent_taskDeleted_pushSuccess() {
        BatchSyncEvent event = createEvent("TASK_DELETED", 1L, "{\"taskId\":1,\"deletedAt\":\"2026-05-09T11:00:00Z\"}");

        when(agentPushService.pushDeleteToAgent(any(), any())).thenReturn(true);

        handler.handleEvent(event);

        verify(agentPushService).pushDeleteToAgent(eq("agent-001"), any());
        verify(eventMapper).updateStatusToCompleted(event.getId());
    }

    // ==================== pushConfigToAgent - 失败场景 ====================

    @Test
    @DisplayName("5. 推送配置失败 - 未超限则调度重试")
    void testHandleEvent_pushFailed_retry() {
        BatchSyncEvent event = createEvent("TASK_CREATED", 1L, "{\"taskId\":1}");
        event.setRetryCount(2);

        when(agentPushService.pushConfigToAgent(any(), any())).thenReturn(false);

        handler.handleEvent(event);

        verify(agentPushService).pushConfigToAgent(eq("agent-001"), any());
        verify(eventMapper, never()).updateStatusToCompleted(any());
        verify(eventMapper).updateRetry(eq(event.getId()), any(), anyString());
    }

    @Test
    @DisplayName("6. 推送配置失败超过最大重试次数 - 标记FAILED")
    void testHandleEvent_pushFailed_maxRetriesExceeded() {
        BatchSyncEvent event = createEvent("TASK_CREATED", 1L, "{\"taskId\":1}");
        event.setRetryCount(10); // 已达最大重试次数

        when(agentPushService.pushConfigToAgent(any(), any())).thenReturn(false);

        handler.handleEvent(event);

        verify(agentPushService).pushConfigToAgent(eq("agent-001"), any());
        verify(eventMapper).updateStatusToFailed(eq(event.getId()), contains("超过最大重试次数"));
    }

    @Test
    @DisplayName("7. Agent地址不存在 - 调度重试并记录错误")
    void testHandleEvent_agentNotFound() {
        BatchSyncEvent event = createEvent("TASK_CREATED", 1L, "{\"taskId\":1}");
        event.setRetryCount(0);

        when(agentPushService.pushConfigToAgent(any(), any())).thenThrow(
            new AgentPushService.AgentNotFoundException("Agent not found: agent-001"));

        handler.handleEvent(event);

        // Agent未找到应该调度重试（因为retryCount=0 < MAX_RETRY_COUNT=10）
        verify(eventMapper).updateRetry(eq(event.getId()), any(), contains("Agent not found"));
    }

    @Test
    @DisplayName("8. 推送异常 - 记录错误信息并调度重试")
    void testHandleEvent_pushException() {
        BatchSyncEvent event = createEvent("TASK_CREATED", 1L, "{\"taskId\":1}");
        event.setRetryCount(1);

        when(agentPushService.pushConfigToAgent(any(), any())).thenThrow(
            new RuntimeException("Connection timeout"));

        handler.handleEvent(event);

        verify(eventMapper).updateRetry(eq(event.getId()), any(), contains("Connection timeout"));
    }

    // ==================== 最大重试次数验证 ====================

    @Test
    @DisplayName("9. 最大重试次数应为10次（符合spec.md）")
    void testMaxRetryCount_is10() {
        assertEquals(10, BatchEventHandler.MAX_RETRY_COUNT, "spec.md要求最大重试次数为10次");
    }

    // ==================== 辅助方法 ====================

    private BatchSyncEvent createEvent(String eventType, Long taskId, String payload) {
        BatchSyncEvent event = new BatchSyncEvent();
        event.setId(100L);
        event.setEventType(eventType);
        event.setTaskId(taskId);
        event.setSourceAgentId("agent-001");
        event.setPayload(payload);
        event.setStatus("PENDING");
        event.setRetryCount(0);
        return event;
    }
}
