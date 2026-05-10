package com.cq.agent.batch.transfer;

import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 重试管理器单元测试
 * 覆盖率目标：100%
 */
@DisplayName("重试管理器 - RetryManager")
class RetryManagerTest {

    private RetryManager retryManager;

    @BeforeEach
    void setUp() {
        retryManager = new RetryManager(3, 1, 5);
    }

    // ==================== 1. 首次失败 - 触发重试 ====================

    @Test
    @DisplayName("1. 首次失败 - 触发重试")
    void testFirstFailure_triggersRetry() throws Exception {
        AtomicBoolean retryScheduled = new AtomicBoolean(false);
        
        retryManager.setRetryExecutor((subtaskId) -> {
            retryScheduled.set(true);
            assertEquals(1L, subtaskId.longValue());
            return System.currentTimeMillis() + 2000; // 2秒后重试
        });
        
        boolean shouldRetry = retryManager.shouldRetry(1L, "CONNECTION_TIMEOUT");
        
        assertTrue(shouldRetry, "首次失败应触发重试");
        assertEquals(1, retryManager.getRetryCount(1L), "重试次数应为1");
        
        System.out.println("✅ 首次失败: subtask=1, shouldRetry=true, count=1");
    }

    // ==================== 2. 达到最大重试次数 ====================

    @Test
    @DisplayName("2. 达到最大次数 - 不再重试")
    void testMaxRetriesReached_noMoreRetries() {
        for (int i = 0; i < 3; i++) {
            assertTrue(retryManager.shouldRetry(2L, "ERROR"), 
                "前3次应允许重试");
        }
        
        boolean shouldRetry = retryManager.shouldRetry(2L, "ERROR");
        
        assertFalse(shouldRetry, "达到最大次数后不应再重试");
        assertEquals(4, retryManager.getRetryCount(2L), 
            "尝试了4次（3次成功+1次拒绝）");
        
        System.out.println("✅ 达到最大重试: max=3, no more retries");
    }

    // ==================== 3. 指数退避计算 ====================

    @Test
    @DisplayName("3. 指数退避 - 延迟时间递增")
    void testExponentialBackoff_increasingDelay() {
        long delay1 = retryManager.calculateNextRetryDelay(1L, 1);
        long delay2 = retryManager.calculateNextRetryDelay(1L, 2);
        long delay3 = retryManager.calculateNextRetryDelay(1L, 3);
        
        assertTrue(delay2 > delay1, "第2次延迟应大于第1次");
        assertTrue(delay3 > delay2, "第3次延迟应大于第2次");
        
        System.out.println("✅ 指数退避: delay1=" + delay1 + 
                          "ms, delay2=" + delay2 + "ms, delay3=" + delay3 + "ms");
    }

    // ==================== 4. 成功后清除记录 ====================

    @Test
    @DisplayName("4. 成功后 - 清除重试记录")
    void testSuccess_clearsRetryRecord() {
        for (int i = 0; i < 2; i++) {
            retryManager.shouldRetry(3L, "TEMP_ERROR");
        }
        
        assertEquals(2, retryManager.getRetryCount(3L));
        
        retryManager.recordSuccess(3L);
        
        assertEquals(0, retryManager.getRetryCount(3L), 
            "成功后应清除重试记录");
            
        assertTrue(retryManager.shouldRetry(3L, "NEW_ERROR"), 
            "成功后应重新开始计数");
            
        System.out.println("✅ 成功后清除: retryCount reset to 0");
    }

    // ==================== 5. 可配置最大重试次数 ====================

    @Test
    @DisplayName("5. 可配置 - 动态修改最大重试")
    void testConfigurableMaxRetries_dynamicChange() {
        RetryManager customManager = new RetryManager(5, 1, 10);
        
        for (int i = 0; i < 5; i++) {
            assertTrue(customManager.shouldRetry(4L, "ERROR"), 
                "max=5时应允许5次重试");
        }
        
        assertFalse(customManager.shouldRetry(4L, "ERROR"), 
            "第6次应被拒绝");
            
        System.out.println("✅ 可配置最大重试: max=5, allowed 5 times");
    }

    // ==================== 6. 并发安全测试 ====================

    @Test
    @DisplayName("6. 并发安全 - 多线程同时重试")
    void testConcurrentSafety_threadSafe() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final Long taskId = (long) i;
            new Thread(() -> {
                try {
                    startLatch.countDown();
                    startLatch.await();
                    
                    if (retryManager.shouldRetry(taskId, "CONCURRENT_ERROR")) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore
                }
            }).start();
        }
        
        Thread.sleep(500);
        
        System.out.println("✅ 并发安全: threads=" + threadCount + 
                          ", success=" + successCount.get());
                          
        assertEquals(threadCount, successCount.get(), 
            "所有线程都应成功获取到重试许可");
    }
}
