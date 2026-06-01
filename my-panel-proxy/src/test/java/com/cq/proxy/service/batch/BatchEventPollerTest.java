package com.cq.proxy.service.batch;

import com.cq.proxy.repository.entity.BatchSyncEvent;
import com.cq.proxy.repository.mapper.BatchSyncEventMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BatchEventPoller 单元测试
 * 符合spec.md设计
 */
class BatchEventPollerTest {

    @Mock
    private BatchSyncEventMapper eventMapper;

    @Mock
    private BatchEventHandler eventHandler;

    @InjectMocks
    private BatchEventPoller poller;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ==================== poll ====================

    @Test
    @DisplayName("1. 无待处理事件 - 正常返回")
    void testPoll_noEvents() {
        when(eventMapper.selectPendingEvents(10)).thenReturn(Collections.emptyList());
        when(eventMapper.selectStuckProcessingEvents(any())).thenReturn(Collections.emptyList());

        poller.poll();

        verify(eventMapper).selectPendingEvents(10);
        verify(eventHandler, never()).handleEvent(any());
    }

    @Test
    @DisplayName("2. 有待处理事件 - 逐个处理")
    void testPoll_withEvents() {
        BatchSyncEvent event1 = createEvent(1L, "TASK_CREATED");
        BatchSyncEvent event2 = createEvent(2L, "TASK_STATUS_CHANGED");
        List<BatchSyncEvent> events = Arrays.asList(event1, event2);

        when(eventMapper.selectPendingEvents(10)).thenReturn(events);
        when(eventMapper.selectStuckProcessingEvents(any())).thenReturn(Collections.emptyList());

        poller.poll();

        verify(eventHandler).handleEvent(event1);
        verify(eventHandler).handleEvent(event2);
    }

    @Test
    @DisplayName("3. EventHandler抛异常 - 不影响其他事件处理")
    void testPoll_handlerException_continues() {
        BatchSyncEvent event1 = createEvent(1L, "TASK_CREATED");
        BatchSyncEvent event2 = createEvent(2L, "TASK_CREATED");

        when(eventMapper.selectPendingEvents(10)).thenReturn(Arrays.asList(event1, event2));
        when(eventMapper.selectStuckProcessingEvents(any())).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("network error")).when(eventHandler).handleEvent(event1);

        poller.poll();

        verify(eventHandler).handleEvent(event1);
        verify(eventHandler).handleEvent(event2);
    }

    // ==================== recoverStuckEvents ====================

    @Test
    @DisplayName("4. 轮询时自动恢复卡住的事件")
    void testPoll_recoversStuckEvents() {
        BatchSyncEvent stuckEvent = createEvent(1L, "TASK_CREATED");
        stuckEvent.setStatus("PROCESSING");

        when(eventMapper.selectPendingEvents(10)).thenReturn(Collections.emptyList());
        when(eventMapper.selectStuckProcessingEvents(any())).thenReturn(List.of(stuckEvent));

        poller.poll();

        verify(eventMapper).updateStatusToPending(1L);
    }

    // ==================== cleanupExpiredEvents ====================

    @Test
    @DisplayName("5. 轮询时自动清理过期事件")
    void testPoll_cleansExpiredEvents() {
        when(eventMapper.selectPendingEvents(10)).thenReturn(Collections.emptyList());
        when(eventMapper.selectStuckProcessingEvents(any())).thenReturn(Collections.emptyList());

        poller.poll();

        verify(eventHandler).cleanupExpiredEvents();
    }

    // ==================== 异常处理 ====================

    @Test
    @DisplayName("6. selectPendingEvents抛异常 - 不崩溃")
    void testPoll_mapperException() {
        when(eventMapper.selectStuckProcessingEvents(any())).thenReturn(Collections.emptyList());
        when(eventMapper.selectPendingEvents(10)).thenThrow(new RuntimeException("db error"));

        assertDoesNotThrow(() -> poller.poll());
    }

    @Test
    @DisplayName("7. selectStuckProcessingEvents抛异常 - 不影响正常轮询")
    void testPoll_recoverException() {
        when(eventMapper.selectStuckProcessingEvents(any())).thenThrow(new RuntimeException("db error"));
        when(eventMapper.selectPendingEvents(10)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> poller.poll());

        verify(eventMapper).selectPendingEvents(10);
    }

    // ==================== 辅助方法 ====================

    private BatchSyncEvent createEvent(Long id, String eventType) {
        BatchSyncEvent event = new BatchSyncEvent();
        event.setId(id);
        event.setEventType(eventType);
        event.setTaskId(1L);
        event.setSourceAgentId("agent-001");
        event.setPayload("{\"taskId\":1,\"sourceAgentName\":\"root@10.240.85.177:7777\"}");
        event.setStatus("PENDING");
        event.setRetryCount(0);
        return event;
    }
}
