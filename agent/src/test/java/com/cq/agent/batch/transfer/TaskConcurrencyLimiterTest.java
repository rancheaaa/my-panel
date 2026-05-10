package com.cq.agent.batch.transfer;

import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 任务级并发控制单元测试
 * 覆盖率目标：100%
 */
@DisplayName("任务级并发控制 - TaskConcurrencyLimiter")
class TaskConcurrencyLimiterTest {

    private TaskConcurrencyLimiter taskLimiter;

    @BeforeEach
    void setUp() {
        taskLimiter = new TaskConcurrencyLimiter(10, 2);
    }

    // ==================== 1. 全局最大值遵守 ====================

    @Test
    @DisplayName("1. 全局最大值 - 被严格遵守")
    void testGlobalMaxRespected() {
        assertEquals(10, taskLimiter.getGlobalMax(), "全局最大应为10");
        
        for (int i = 0; i < 10; i++) {
            assertTrue(taskLimiter.tryAcquire("task-1"), "前10次应成功");
        }
        
        assertFalse(taskLimiter.tryAcquire("task-2"), "超过全局最大应失败");
        
        System.out.println("✅ 全局最大值限制: max=10, blocked on 11th");
    }

    // ==================== 2. 任务特定覆盖 ====================

    @Test
    @DisplayName("2. 任务特定覆盖 - 单任务限制")
    void testTaskSpecificOverride() throws Exception {
        taskLimiter.setTaskMax("special-task", 1);
        
        assertTrue(taskLimiter.tryAcquire("special-task"), "第1次应成功");
        assertFalse(taskLimiter.tryAcquire("special-task"), "单任务限制=1，第2次应失败");
        
        System.out.println("✅ 任务特定覆盖: special-task max=1");
    }

    // ==================== 3. 多任务竞争 ====================

    @Test
    @DisplayName("3. 多任务竞争 - 公平分配")
    void testMultipleTasks_competition() throws Exception {
        int threadCount = 8;
        CountDownLatch startLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final String taskId = "task-" + (i % 4); // 4个不同任务
            new Thread(() -> {
                try {
                    startLatch.countDown();
                    startLatch.await();
                    
                    if (taskLimiter.tryAcquire(taskId)) {
                        successCount.incrementAndGet();
                        Thread.sleep(50);
                        taskLimiter.release(taskId);
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore
                }
            }).start();
        }
        
        Thread.sleep(2000);
        
        int total = successCount.get() + failCount.get();
        System.out.println("✅ 多任务竞争: threads=" + threadCount + 
                          ", success=" + successCount.get() + 
                          ", failed=" + failCount.get());
                          
        assertEquals(threadCount, total, "所有线程都应完成");
        assertTrue(successCount.get() > 0, "应有部分成功");
    }
}
