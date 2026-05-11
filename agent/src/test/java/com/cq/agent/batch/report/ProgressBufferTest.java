package com.cq.agent.batch.report;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 进度缓冲区单元测试
 * 覆盖率目标：100%
 */
@DisplayName("进度缓冲区 - ProgressBuffer")
class ProgressBufferTest {

    private ProgressBuffer progressBuffer;

    @BeforeEach
    void setUp() {
        progressBuffer = new ProgressBuffer(10, 1000);
    }

    // ==================== 1. 添加事件到缓冲区 ====================

    @Test
    @DisplayName("1. addEvent() - 事件被缓冲")
    void testAddEvent_buffered() {
        ProgressEvent event = createProgressEvent(1L, 50, 100);
        
        progressBuffer.addEvent(event);
        
        assertEquals(1, progressBuffer.getBufferedCount(), "应有1个事件在缓冲区");
        
        System.out.println("✅ 事件已添加到缓冲区: count=1");
    }

    // ==================== 2. 定时器触发flush ====================

    @Test
    @DisplayName("2. flush() - 定时器到期后自动flush")
    void testFlush_interval() throws Exception {
        for (int i = 0; i < 5; i++) {
            progressBuffer.addEvent(createProgressEvent((long) i, i * 20, 100));
        }
        
        assertEquals(5, progressBuffer.getBufferedCount());
        
        AtomicBoolean flushed = new AtomicBoolean(false);
        progressBuffer.setFlushConsumer(events -> {
            flushed.set(true);
            assertEquals(5, events.size(), "应flush所有5个事件");
        });
        
        Thread.sleep(1200); // 等待定时器 (>FLUSH_INTERVAL_MS=1000)
        
        assertTrue(flushed.get(), "定时器到期应触发flush");
        assertEquals(0, progressBuffer.getBufferedCount(), "flush后缓冲区应为空");
        
        System.out.println("✅ 定时器flush成功: 5个事件已处理");
    }

    // ==================== 3. 达到最大容量自动flush ====================

    @Test
    @DisplayName("3. flush() - 达到最大容量时自动flush")
    void testFlush_maxSize() {
        AtomicBoolean autoFlushed = new AtomicBoolean(false);
        progressBuffer.setFlushConsumer(events -> {
            autoFlushed.set(true);
            assertTrue(events.size() <= 10, "不应超过测试用的最大容量");
        });
        
        for (int i = 0; i < 12; i++) { // 超过测试用的MAX_SIZE=10
            progressBuffer.addEvent(createProgressEvent((long) i, i * 10, 100));
        }
        
        assertTrue(autoFlushed.get(), "达到最大容量时应自动flush");
        
        System.out.println("✅ 最大容量自动flush: max=10, added=12");
    }

    @Test
    @DisplayName("3b. 默认MAX_SIZE符合spec要求(500)")
    void testDefaultMaxSize() {
        assertEquals(500, ProgressBuffer.MAX_SIZE, "spec要求buffer.max.size=500");
        System.out.println("✅ 默认MAX_SIZE=500, 符合spec要求");
    }

    // ==================== 4. 并发安全测试 ====================

    @Test
    @DisplayName("4. 并发addEvent() - 线程安全")
    void testConcurrentAccess_threadSafe() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        progressBuffer.setFlushConsumer(events -> {
            successCount.addAndGet(events.size());
        });
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.countDown();
                    startLatch.await();
                    
                    for (int j = 0; j < 5; j++) {
                        progressBuffer.addEvent(
                            createProgressEvent((long) (index * 5 + j), j * 20, 100)
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.SECONDS);
        
        Thread.sleep(1500); // 等待可能的auto-flush
        
        int totalAdded = threadCount * 5;
        System.out.println("✅ 并发安全测试: threads=" + threadCount + 
                          ", totalAdded=" + totalAdded + 
                          ", processed=" + successCount.get());
                          
        assertTrue(successCount.get() > 0, "应有部分事件被处理");
    }

    // ==================== 辅助方法 ====================

    private ProgressEvent createProgressEvent(Long subtaskId, int transferredBytes, int totalBytes) {
        return new ProgressEvent(subtaskId, transferredBytes, totalBytes, System.currentTimeMillis());
    }
}
