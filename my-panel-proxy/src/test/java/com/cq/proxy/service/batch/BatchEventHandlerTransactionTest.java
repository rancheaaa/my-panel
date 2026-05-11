package com.cq.proxy.service.batch;

import com.cq.proxy.repository.entity.BatchSyncEvent;
import com.cq.proxy.repository.mapper.BatchSyncEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BatchEventHandler 事务一致性测试
 * 验证事件处理的事务边界：
 * 1. Agent确认持久化成功 → 标记COMPLETED
 * 2. Agent未确认持久化 → 调度重试
 * 3. 推送异常 → 调度重试
 * 4. 超过最大重试次数 → 标记FAILED
 */
@DisplayName("BatchEventHandler - 事务一致性验证")
class BatchEventHandlerTransactionTest {

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

    // ==================== 1. 成功场景 ====================

    @Test
    @DisplayName("1. Agent确认持久化 - 标记COMPLETED")
    void testHandleEvent_agentPersisted_markCompleted() {
        BatchSyncEvent event = createEvent("TASK_CREATED", 1L, "{\"taskId\":1}");

        when(agentPushService.pushConfigToAgent(any(), any())).thenReturn(true);

        handler.handleEvent(event);

        // 验证：先标记PROCESSING，然后标记COMPLETED
        verify(eventMapper).updateStatusToProcessing(event);
        verify(agentPushService).pushConfigToAgent(eq("agent-001"), any());
        verify(eventMapper).updateStatusToCompleted(event.getId());
        verify(eventMapper, never()).updateRetry(any(), any(), any());
        verify(eventMapper, never()).updateStatusToFailed(any(), any());
    }

    @Test
    @DisplayName("2. Agent未确认持久化 - 调度重试")
    void testHandleEvent_agentNotPersisted_scheduleRetry() {
        BatchSyncEvent event = createEvent("TASK_CREATED", 1L, "{\"taskId\":1}");
        event.setRetryCount(2);

        when(agentPushService.pushConfigToAgent(any(), any())).thenReturn(false);

        handler.handleEvent(event);

        // 验证：标记PROCESSING，推送失败，调度重试
        verify(eventMapper).updateStatusToProcessing(event);
        verify(agentPushService).pushConfigToAgent(eq("agent-001"), any());
        verify(eventMapper, never()).updateStatusToCompleted(any());
        verify(eventMapper).updateRetry(eq(event.getId()), any(), any());
        verify(eventMapper, never()).updateStatusToFailed(any(), any());
    }

    // ==================== 2. 失败场景 ====================

    @Test
    @DisplayName("3. 推送异常 - 调度重试")
    void testHandleEvent_pushException_scheduleRetry() {
        BatchSyncEvent event = createEvent("TASK_CREATED", 1L, "{\"taskId\":1}");
        event.setRetryCount(1);

        when(agentPushService.pushConfigToAgent(any(), any()))
            .thenThrow(new RuntimeException("Connection timeout"));

        handler.handleEvent(event);

        // 验证：异常后调度重试
        verify(eventMapper).updateStatusToProcessing(event);
        verify(eventMapper).updateRetry(eq(event.getId()), any(), contains("Connection timeout"));
        verify(eventMapper, never()).updateStatusToCompleted(any());
        verify(eventMapper, never()).updateStatusToFailed(any(), any());
    }

    @Test
    @DisplayName("4. 超过最大重试次数 - 标记FAILED")
    void testHandleEvent_maxRetriesExceeded_markFailed() {
        BatchSyncEvent event = createEvent("TASK_CREATED", 1L, "{\"taskId\":1}");
        event.setRetryCount(10); // 已达最大重试次数

        when(agentPushService.pushConfigToAgent(any(), any())).thenReturn(false);

        handler.handleEvent(event);

        // 验证：超过最大重试次数，标记FAILED
        verify(eventMapper).updateStatusToProcessing(event);
        verify(eventMapper).updateStatusToFailed(eq(event.getId()), contains("超过最大重试次数"));
        verify(eventMapper, never()).updateStatusToCompleted(any());
        verify(eventMapper, never()).updateRetry(any(), any(), any());
    }

    // ==================== 3. 删除指令场景 ====================

    @Test
    @DisplayName("5. 删除指令成功 - 标记COMPLETED")
    void testHandleEvent_deleteSuccess_markCompleted() {
        BatchSyncEvent event = createEvent("TASK_DELETED", 1L, "{\"taskId\":1}");

        when(agentPushService.pushDeleteToAgent(any(), any())).thenReturn(true);

        handler.handleEvent(event);

        verify(eventMapper).updateStatusToProcessing(event);
        verify(agentPushService).pushDeleteToAgent(eq("agent-001"), any());
        verify(eventMapper).updateStatusToCompleted(event.getId());
    }

    @Test
    @DisplayName("6. 删除指令失败 - 调度重试")
    void testHandleEvent_deleteFailed_scheduleRetry() {
        BatchSyncEvent event = createEvent("TASK_DELETED", 1L, "{\"taskId\":1}");
        event.setRetryCount(3);

        when(agentPushService.pushDeleteToAgent(any(), any())).thenReturn(false);

        handler.handleEvent(event);

        verify(eventMapper).updateStatusToProcessing(event);
        verify(agentPushService).pushDeleteToAgent(eq("agent-001"), any());
        verify(eventMapper).updateRetry(eq(event.getId()), any(), any());
        verify(eventMapper, never()).updateStatusToCompleted(any());
    }

    // ==================== 4. 边界条件 ====================

    @Test
    @DisplayName("7. 首次重试(retryCount=0)失败 - 调度重试")
    void testHandleEvent_firstRetry_scheduleRetry() {
        BatchSyncEvent event = createEvent("TASK_CREATED", 1L, "{\"taskId\":1}");
        event.setRetryCount(0);

        when(agentPushService.pushConfigToAgent(any(), any())).thenReturn(false);

        handler.handleEvent(event);

        verify(eventMapper).updateRetry(eq(event.getId()), any(), any());
        verify(eventMapper, never()).updateStatusToFailed(any(), any());
    }

    @Test
    @DisplayName("8. 最后一次重试(retryCount=9)失败 - 标记FAILED")
    void testHandleEvent_lastRetryFailed_markFailed() {
        BatchSyncEvent event = createEvent("TASK_CREATED", 1L, "{\"taskId\":1}");
        event.setRetryCount(9); // 第10次尝试（retryCount从0开始）

        when(agentPushService.pushConfigToAgent(any(), any())).thenReturn(false);

        handler.handleEvent(event);

        verify(eventMapper).updateStatusToFailed(any(), any());
        verify(eventMapper, never()).updateRetry(any(), any(), any());
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
