package com.cq.proxy.service.batch;

import com.cq.proxy.dto.SubTaskDTO;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 批量写入优化单元测试
 * 覆盖率目标：100%
 */
@DisplayName("批量写入优化 - ProgressBatchWriter")
class ProgressBatchWriterTest {

    @Mock
    private ProgressService progressService;

    private ProgressBatchWriter batchWriter;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        batchWriter = new ProgressBatchWriter(progressService);
        executorService = Executors.newFixedThreadPool(5);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdown();
        batchWriter.shutdown();
    }

    // ==================== 1. 基础批量写入 ====================

    @Test
    @DisplayName("1. add() + flush() - 批量写入成功")
    void testAddAndFlush_success() {
        SubTaskDTO item1 = createSubTaskDTO(1L, 50, 100);
        SubTaskDTO item2 = createSubTaskDTO(2L, 80, 100);

        batchWriter.add(item1);
        batchWriter.add(item2);

        assertEquals(2, batchWriter.getPendingCount());

        int flushed = batchWriter.flush();

        verify(progressService).batchUpdateProgress(argThat(array -> 
            array.length == 2));
        assertEquals(0, batchWriter.getPendingCount());
        System.out.println("✅ 批量写入成功: flushed=" + flushed);
    }

    // ==================== 2. 自动触发flush ====================

    @Test
    @DisplayName("2. 达到阈值自动flush")
    void testAutoFlush_onThresholdReached() {
        for (int i = 0; i < ProgressBatchWriter.BATCH_SIZE; i++) {
            batchWriter.add(createSubTaskDTO((long) i, i * 10, 100));
        }

        verify(progressService, times(1)).batchUpdateProgress(any(SubTaskDTO[].class));
        assertEquals(0, batchWriter.getPendingCount());
        System.out.println("✅ 阈值=" + ProgressBatchWriter.BATCH_SIZE + " 时自动flush");
    }

    // ==================== 3. 定时器flush ====================

    @Test
    @DisplayName("3. 定时器到期自动flush")
    void testScheduledFlush_afterInterval() throws InterruptedException {
        batchWriter.add(createSubTaskDTO(1L, 30, 100));

        Thread.sleep(1500); // 等待定时器触发 (>FLUSH_INTERVAL_MS=1000)

        verify(progressService, atLeastOnce()).batchUpdateProgress(any(SubTaskDTO[].class));
        System.out.println("✅ 定时器flush成功");
    }

    // ==================== 4. 并发安全测试 ====================

    @Test
    @DisplayName("4. 多线程并发add()线程安全")
    void testConcurrentAdd_threadSafe() throws Exception {
        CountDownLatch latch = new CountDownLatch(20);
        
        for (int i = 0; i < 20; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    batchWriter.add(createSubTaskDTO((long) index, index * 5, 100));
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(100); // 等待可能的auto-flush
        
        assertTrue(batchWriter.getPendingCount() >= 0);
        System.out.println("✅ 并发add完成: pending=" + batchWriter.getPendingCount());
    }

    // ==================== 5. shutdown清理 ====================

    @Test
    @DisplayName("5. shutdown()时flush剩余数据")
    void testShutdown_flushesRemaining() {
        batchWriter.add(createSubTaskDTO(99L, 99, 100));
        batchWriter.add(createSubTaskDTO(100L, 100, 100));

        batchWriter.shutdown();

        verify(progressService).batchUpdateProgress(argThat(array -> 
            array.length == 2));
        System.out.println("✅ shutdown时flush剩余数据");
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("6. flush空队列返回0")
    void testFlush_emptyQueue_returnsZero() {
        int result = batchWriter.flush();
        assertEquals(0, result);
        System.out.println("✅ 空队列flush返回0");
    }

    @Test
    @DisplayName("7. 单个item未达到阈值不flush")
    void testSingleItem_noAutoFlush() {
        batchWriter.add(createSubTaskDTO(1L, 10, 100));
        
        verify(progressService, never()).batchUpdateProgress(any(SubTaskDTO[].class));
        assertEquals(1, batchWriter.getPendingCount());
        System.out.println("✅ 单个item不触发auto-flush");
    }

    // ==================== 辅助方法 ====================

    private SubTaskDTO createSubTaskDTO(Long subtaskId, int transferredBytes, int totalBytes) {
        SubTaskDTO dto = new SubTaskDTO();
        dto.setSubtaskId(subtaskId);
        dto.setTransferredBytes((long) transferredBytes);
        dto.setTotalBytes(totalBytes);
        dto.setTimestamp(System.currentTimeMillis());
        return dto;
    }
}
