package com.cq.proxy.service.batch;

import com.cq.proxy.repository.entity.BatchSyncEvent;
import com.cq.proxy.repository.mapper.BatchSyncEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BatchEventHandler符合spec.md的单元测试
 * 验证事件处理：重试策略、过期清理、状态转换
 */
@DisplayName("批量事件处理器 - 符合spec.md设计")
class BatchEventHandlerSpecTest {

    private BatchEventHandler eventHandler;
    private BatchSyncEventMapper eventMapper;
    private AgentPushService agentPushService;

    @BeforeEach
    void setUp() {
        eventMapper = mock(BatchSyncEventMapper.class);
        agentPushService = mock(AgentPushService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        eventHandler = new BatchEventHandler(eventMapper, objectMapper, agentPushService);

        // 默认模拟推送成功
        when(agentPushService.pushConfigToAgent(any(), any())).thenReturn(true);
        when(agentPushService.pushDeleteToAgent(any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("1. 处理PENDING事件 - 标记为PROCESSING")
    void testHandlePendingEvent() {
        BatchSyncEvent event = createEvent(1L, "TASK_CREATED", "PENDING");

        eventHandler.handleEvent(event);

        ArgumentCaptor<BatchSyncEvent> captor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).updateStatusToProcessing(captor.capture());

        BatchSyncEvent updated = captor.getValue();
        assertEquals(1L, updated.getId());
        assertEquals("PROCESSING", updated.getStatus());

        System.out.println("✅ PENDING事件处理: 标记为PROCESSING");
    }

    @Test
    @DisplayName("2. 处理失败 - 重试次数增加，设置下次可处理时间（指数退避）")
    void testHandleFailure_retryBackoff() {
        BatchSyncEvent event = createEvent(1L, "TASK_CREATED", "PROCESSING");
        event.setRetryCount(2);

        // 模拟处理失败（抛出异常）
        doThrow(new RuntimeException("推送失败")).when(eventMapper).updateStatusToProcessing(any());

        try {
            eventHandler.handleEvent(event);
        } catch (Exception e) {
            // 预期异常
        }

        // 验证重试逻辑
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventMapper, atLeastOnce()).updateRetry(idCaptor.capture(), dateCaptor.capture(), msgCaptor.capture());

        assertEquals(1L, idCaptor.getValue(), "应更新正确的事件ID");
        assertNotNull(dateCaptor.getValue(), "应设置下次重试时间");
        assertNotNull(msgCaptor.getValue(), "应记录错误信息");

        System.out.println("✅ 失败重试: 已调度下次重试");
    }

    @Test
    @DisplayName("3. 最大重试次数 - 超过后标记为FAILED")
    void testMaxRetries_markFailed() {
        BatchSyncEvent event = createEvent(1L, "TASK_CREATED", "PROCESSING");
        event.setRetryCount(10); // 最大重试10次

        // 模拟处理失败
        doThrow(new RuntimeException("推送失败")).when(eventMapper).updateStatusToProcessing(any());

        try {
            eventHandler.handleEvent(event);
        } catch (Exception e) {
            // 预期异常
        }

        // 验证是否标记为FAILED
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        verify(eventMapper, atLeastOnce()).updateStatusToFailed(idCaptor.capture(), anyString());

        assertEquals(1L, idCaptor.getValue());

        System.out.println("✅ 超过最大重试: 标记为FAILED");
    }

    @Test
    @DisplayName("4. 清理过期事件 - 标记为FAILED")
    void testCleanupExpiredEvents() {
        BatchSyncEvent expiredEvent = createEvent(1L, "TASK_CREATED", "PENDING");
        expiredEvent.setExpireAt(new Date(System.currentTimeMillis() - 1000)); // 已过期

        when(eventMapper.selectExpiredEvents(any())).thenReturn(List.of(expiredEvent));

        eventHandler.cleanupExpiredEvents();

        verify(eventMapper).updateStatusToFailed(eq(1L), eq("事件过期"));

        System.out.println("✅ 过期事件清理: 标记为FAILED");
    }

    @Test
    @DisplayName("5. 恢复卡住的PROCESSING事件 - 标记为PENDING")
    void testRecoverStuckProcessing() {
        BatchSyncEvent stuckEvent = createEvent(1L, "TASK_CREATED", "PROCESSING");
        stuckEvent.setStartedAt(new Date(System.currentTimeMillis() - 10 * 60 * 1000)); // 10分钟前开始

        when(eventMapper.selectStuckProcessingEvents(any())).thenReturn(List.of(stuckEvent));

        eventHandler.recoverStuckProcessing();

        verify(eventMapper).updateStatusToPending(1L);

        System.out.println("✅ 恢复卡住事件: PROCESSING→PENDING");
    }

    @Test
    @DisplayName("6. 指数退避计算 - 1s, 2s, 4s, 8s...上限60s")
    void testExponentialBackoff() {
        // 第1次重试：1秒
        long delay1 = eventHandler.calculateRetryDelay(1);
        assertEquals(TimeUnit.SECONDS.toMillis(1), delay1, "第1次应为1秒");

        // 第2次重试：2秒
        long delay2 = eventHandler.calculateRetryDelay(2);
        assertEquals(TimeUnit.SECONDS.toMillis(2), delay2, "第2次应为2秒");

        // 第3次重试：4秒
        long delay3 = eventHandler.calculateRetryDelay(3);
        assertEquals(TimeUnit.SECONDS.toMillis(4), delay3, "第3次应为4秒");

        // 第4次重试：8秒
        long delay4 = eventHandler.calculateRetryDelay(4);
        assertEquals(TimeUnit.SECONDS.toMillis(8), delay4, "第4次应为8秒");

        // 第10次重试：不超过60秒
        long delay10 = eventHandler.calculateRetryDelay(10);
        assertTrue(delay10 <= TimeUnit.SECONDS.toMillis(60), "不应超过60秒");

        System.out.println("✅ 指数退避: 1s→2s→4s→8s...上限60s");
    }

    private BatchSyncEvent createEvent(Long id, String eventType, String status) {
        BatchSyncEvent event = new BatchSyncEvent();
        event.setId(id);
        event.setEventType(eventType);
        event.setTaskId(1001L);
        event.setSourceAgentId("agent-001");
        event.setStatus(status);
        event.setRetryCount(0);
        event.setPayload("{}");
        return event;
    }
}
