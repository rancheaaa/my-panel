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
 * BatchEventHandler 单元测试
 * 符合spec.md设计
 */
class BatchEventHandlerTest {

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

        // 默认模拟推送成功
        when(agentPushService.pushConfigToAgent(any(), any())).thenReturn(true);
        when(agentPushService.pushDeleteToAgent(any(), any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ==================== handleEvent - 正常流程 ====================

    @Test
    @DisplayName("1. TASK_CREATED - 正常处理并标记COMPLETED")
    void testHandleEvent_taskCreated() {
        BatchSyncEvent event = createEvent("TASK_CREATED", 1L);

        handler.handleEvent(event);

        verify(eventMapper).updateStatusToProcessing(any());
        verify(eventMapper).updateStatusToCompleted(event.getId());
    }

    @Test
    @DisplayName("2. TASK_DELETED - 正常处理并标记COMPLETED")
    void testHandleEvent_taskDeleted() {
        BatchSyncEvent event = createEvent("TASK_DELETED", 4L);

        handler.handleEvent(event);

        verify(eventMapper).updateStatusToProcessing(any());
        verify(eventMapper).updateStatusToCompleted(event.getId());
    }

    // ==================== handleEvent - 未知事件类型 ====================

    @Test
    @DisplayName("3. 未知事件类型 - 调度重试")
    void testHandleEvent_unknownType() {
        BatchSyncEvent event = createEvent("UNKNOWN_TYPE", 1L);
        event.setRetryCount(10); // 超过最大重试次数

        handler.handleEvent(event);

        verify(eventMapper).updateStatusToFailed(eq(event.getId()), contains("超过最大重试次数"));
    }

    // ==================== handleEvent - 超过最大重试次数 ====================

    @Test
    @DisplayName("4. 超过最大重试次数 - 标记FAILED")
    void testHandleEvent_maxRetriesExceeded() {
        BatchSyncEvent event = createEvent("TASK_CREATED", 1L);
        event.setPayload("{\"taskId\":1}");
        event.setRetryCount(10);

        // 模拟处理时抛出异常
        doThrow(new RuntimeException("推送失败")).when(eventMapper).updateStatusToProcessing(any());

        handler.handleEvent(event);

        verify(eventMapper).updateStatusToFailed(eq(event.getId()), contains("超过最大重试次数"));
    }

    // ==================== calculateRetryDelay ====================

    @Test
    @DisplayName("5. 指数退避计算 - 1s, 2s, 4s, 8s...上限60s")
    void testCalculateRetryDelay() {
        assertEquals(1000, handler.calculateRetryDelay(1), "第1次应为1秒");
        assertEquals(2000, handler.calculateRetryDelay(2), "第2次应为2秒");
        assertEquals(4000, handler.calculateRetryDelay(3), "第3次应为4秒");
        assertEquals(8000, handler.calculateRetryDelay(4), "第4次应为8秒");
        assertTrue(handler.calculateRetryDelay(10) <= 60000, "不应超过60秒");
    }

    // ==================== 辅助方法 ====================

    private BatchSyncEvent createEvent(String eventType, Long taskId) {
        BatchSyncEvent event = new BatchSyncEvent();
        event.setId(100L);
        event.setEventType(eventType);
        event.setTaskId(taskId);
        event.setSourceAgentId("agent-001");
        event.setPayload("{\"taskId\":" + taskId + ",\"sourceAgentName\":\"root@10.240.85.177:7777\"}");
        event.setStatus("PENDING");
        event.setRetryCount(0);
        return event;
    }
}
