package com.cq.proxy.service.batch;

import com.cq.proxy.repository.entity.BatchSyncEvent;
import com.cq.proxy.repository.mapper.BatchSyncEventMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TDD测试：验证BatchEventPoller是否符合spec.md设计
 * 核心要求：
 * 1. 使用FOR UPDATE SKIP LOCKED避免并发消费
 * 2. 每3-5秒轮询一次
 * 3. 批量拉取（如10条）
 * 4. 异步处理每个事件（不阻塞轮询线程）
 */
@DisplayName("BatchEventPoller - 符合spec.md设计")
class BatchEventPollerSpecTest {

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

    // ==================== Red Phase: spec.md要求验证 ====================

    @Test
    @DisplayName("1. [spec.md] 轮询应使用FOR UPDATE SKIP LOCKED")
    void testPoll_shouldUseForUpdateSkipLocked() {
        // Given
        when(eventMapper.selectPendingEvents(anyInt())).thenReturn(Collections.emptyList());

        // When
        poller.poll();

        // Then: 验证使用了selectPendingEvents（内部应实现FOR UPDATE SKIP LOCKED）
        verify(eventMapper).selectPendingEvents(anyInt());
        // 注意：FOR UPDATE SKIP LOCKED的具体实现在SQL层，这里验证调用了正确的方法
    }

    @Test
    @DisplayName("2. [spec.md] 每次最多拉取10条事件")
    void testPoll_shouldLimitBatchSizeTo10() {
        // Given
        when(eventMapper.selectPendingEvents(anyInt())).thenReturn(Collections.emptyList());

        // When
        poller.poll();

        // Then: 验证batch size为10
        verify(eventMapper).selectPendingEvents(eq(10));
    }

    @Test
    @DisplayName("3. [spec.md] 拉取到事件后应分发给EventHandler处理")
    void testPoll_shouldDispatchEventsToHandler() {
        // Given
        BatchSyncEvent event1 = createEvent(1L, "TASK_CREATED");
        BatchSyncEvent event2 = createEvent(2L, "TASK_UPDATED");
        when(eventMapper.selectPendingEvents(10)).thenReturn(Arrays.asList(event1, event2));

        // When
        poller.poll();

        // Then: 验证每个事件都被处理
        verify(eventHandler).handleEvent(event1);
        verify(eventHandler).handleEvent(event2);
        verify(eventMapper).selectPendingEvents(10);
    }

    @Test
    @DisplayName("4. [spec.md] 单个事件处理异常不应影响其他事件")
    void testPoll_singleEventException_shouldNotAffectOthers() {
        // Given
        BatchSyncEvent event1 = createEvent(1L, "TASK_CREATED");
        BatchSyncEvent event2 = createEvent(2L, "TASK_UPDATED");
        when(eventMapper.selectPendingEvents(10)).thenReturn(Arrays.asList(event1, event2));
        doThrow(new RuntimeException("处理失败")).when(eventHandler).handleEvent(event1);

        // When & Then: 不应抛出异常，event2仍应被处理
        assertDoesNotThrow(() -> poller.poll());
        verify(eventHandler).handleEvent(event1);
        verify(eventHandler).handleEvent(event2);
    }

    @Test
    @DisplayName("5. [spec.md] 无待处理事件时应空转")
    void testPoll_noEvents_shouldDoNothing() {
        // Given
        when(eventMapper.selectPendingEvents(10)).thenReturn(Collections.emptyList());

        // When
        poller.poll();

        // Then: 不调用eventHandler
        verify(eventHandler, never()).handleEvent(any());
    }

    @Test
    @DisplayName("6. [spec.md] 应先清理过期事件")
    void testPoll_shouldCleanupExpiredEventsFirst() {
        // Given
        when(eventMapper.selectPendingEvents(anyInt())).thenReturn(Collections.emptyList());

        // When
        poller.poll();

        // Then: 验证先调用cleanup
        InOrder inOrder = inOrder(eventHandler, eventMapper);
        inOrder.verify(eventHandler).cleanupExpiredEvents();
        inOrder.verify(eventMapper).selectPendingEvents(anyInt());
    }

    @Test
    @DisplayName("7. [spec.md] 应恢复卡住的PROCESSING事件")
    void testPoll_shouldRecoverStuckEvents() {
        // Given
        when(eventMapper.selectPendingEvents(anyInt())).thenReturn(Collections.emptyList());
        when(eventMapper.selectStuckProcessingEvents(any())).thenReturn(Collections.emptyList());

        // When
        poller.poll();

        // Then: 验证调用恢复卡住事件的方法
        verify(eventMapper).selectStuckProcessingEvents(any());
    }

    @Test
    @DisplayName("8. [spec.md] 轮询异常不应导致整个服务崩溃")
    void testPoll_exception_shouldNotCrash() {
        // Given
        when(eventMapper.selectPendingEvents(anyInt())).thenThrow(new RuntimeException("数据库异常"));

        // When & Then: 不应抛出异常
        assertDoesNotThrow(() -> poller.poll());
    }

    // ==================== 辅助方法 ====================

    private BatchSyncEvent createEvent(Long id, String eventType) {
        BatchSyncEvent event = new BatchSyncEvent();
        event.setId(id);
        event.setEventType(eventType);
        event.setTaskId(id);
        event.setSourceAgentId("agent-001");
        event.setStatus("PENDING");
        event.setRetryCount(0);
        return event;
    }
}
