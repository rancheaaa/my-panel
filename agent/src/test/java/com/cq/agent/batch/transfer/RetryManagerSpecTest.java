package com.cq.agent.batch.transfer;

import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RetryManager符合spec.md的单元测试
 * 验证重试策略：指数退避、最大重试天数、带宽降低
 */
@DisplayName("重试管理器 - 符合spec.md设计")
class RetryManagerSpecTest {

    @Test
    @DisplayName("1. 指数退避 - 首次等待intervalMin分钟，后续翻倍")
    void testExponentialBackoff() {
        // maxRetries=10, intervalMin=30分钟, maxDays=7
        RetryManager retryManager = new RetryManager(10, 30, 7);

        // 第1次重试：30分钟
        long delay1 = retryManager.calculateNextRetryDelay(1L, 1);
        assertEquals(TimeUnit.MINUTES.toMillis(30), delay1, "第1次重试应等待30分钟");

        // 第2次重试：60分钟
        long delay2 = retryManager.calculateNextRetryDelay(1L, 2);
        assertEquals(TimeUnit.MINUTES.toMillis(60), delay2, "第2次重试应等待60分钟");

        // 第3次重试：120分钟
        long delay3 = retryManager.calculateNextRetryDelay(1L, 3);
        assertEquals(TimeUnit.MINUTES.toMillis(120), delay3, "第3次重试应等待120分钟");

        // 第4次重试：240分钟
        long delay4 = retryManager.calculateNextRetryDelay(1L, 4);
        assertEquals(TimeUnit.MINUTES.toMillis(240), delay4, "第4次重试应等待240分钟");

        System.out.println("✅ 指数退避验证: 30min→60min→120min→240min");
    }

    @Test
    @DisplayName("2. 退避上限 - 不超过2小时")
    void testBackoffUpperLimit() {
        RetryManager retryManager = new RetryManager(10, 30, 2);

        // 多次重试后应达到上限2小时
        long delay5 = retryManager.calculateNextRetryDelay(1L, 5);
        assertTrue(delay5 <= TimeUnit.HOURS.toMillis(2), "退避不应超过2小时");

        long delay10 = retryManager.calculateNextRetryDelay(1L, 10);
        assertTrue(delay10 <= TimeUnit.HOURS.toMillis(2), "退避不应超过2小时");

        System.out.println("✅ 退避上限验证: delay5=" + delay5 + "ms, delay10=" + delay10 + "ms");
    }

    @Test
    @DisplayName("3. 最大重试次数 - 超过后不应重试")
    void testMaxRetries() {
        RetryManager retryManager = new RetryManager(3, 30, 7);

        assertTrue(retryManager.shouldRetry(1L, "网络超时"), "第1次应重试");
        assertTrue(retryManager.shouldRetry(1L, "网络超时"), "第2次应重试");
        assertTrue(retryManager.shouldRetry(1L, "网络超时"), "第3次应重试");
        assertFalse(retryManager.shouldRetry(1L, "网络超时"), "第4次不应重试");

        assertEquals(4, retryManager.getRetryCount(1L), "重试次数应为4");

        System.out.println("✅ 最大重试次数验证: max=3, attempts=4");
    }

    @Test
    @DisplayName("4. 记录成功 - 清除重试计数")
    void testRecordSuccess() {
        RetryManager retryManager = new RetryManager(10, 30, 7);

        retryManager.shouldRetry(1L, "网络超时");
        retryManager.shouldRetry(1L, "网络超时");
        assertEquals(2, retryManager.getRetryCount(1L));

        retryManager.recordSuccess(1L);
        assertEquals(0, retryManager.getRetryCount(1L), "成功后重试计数应清除");

        System.out.println("✅ 记录成功验证: 重试计数已清除");
    }

    @Test
    @DisplayName("5. 重试执行器 - 调度下次重试")
    void testRetryExecutor() {
        RetryManager retryManager = new RetryManager(3, 30, 7);
        AtomicBoolean executorCalled = new AtomicBoolean(false);
        AtomicInteger scheduledSubtaskId = new AtomicInteger(-1);

        retryManager.setRetryExecutor(subtaskId -> {
            executorCalled.set(true);
            scheduledSubtaskId.set(subtaskId.intValue());
            return System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30);
        });

        boolean shouldRetry = retryManager.shouldRetry(1001L, "连接失败");

        assertTrue(shouldRetry, "应允许重试");
        assertTrue(executorCalled.get(), "重试执行器应被调用");
        assertEquals(1001, scheduledSubtaskId.get(), "应传递正确的subtaskId");

        System.out.println("✅ 重试执行器验证: subtask=1001");
    }

    @Test
    @DisplayName("6. 多个子任务独立计数")
    void testMultipleSubtasksIndependent() {
        RetryManager retryManager = new RetryManager(10, 30, 7);

        retryManager.shouldRetry(1L, "错误1");
        retryManager.shouldRetry(1L, "错误2");
        retryManager.shouldRetry(2L, "错误3");

        assertEquals(2, retryManager.getRetryCount(1L), "子任务1应重试2次");
        assertEquals(1, retryManager.getRetryCount(2L), "子任务2应重试1次");

        System.out.println("✅ 多子任务独立计数: subtask1=2, subtask2=1");
    }

    @Test
    @DisplayName("7. 清除所有记录")
    void testClearAll() {
        RetryManager retryManager = new RetryManager(10, 30, 7);

        retryManager.shouldRetry(1L, "错误");
        retryManager.shouldRetry(2L, "错误");

        retryManager.clearAll();

        assertEquals(0, retryManager.getRetryCount(1L), "清除后应为0");
        assertEquals(0, retryManager.getRetryCount(2L), "清除后应为0");

        System.out.println("✅ 清除所有记录验证");
    }
}
