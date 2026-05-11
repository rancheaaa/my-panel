package com.cq.agent.batch.transfer;

import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD测试：验证TaskConcurrencyLimiter任务级并发控制
 * 核心要求（spec.md）：
 * 1. 全局最大并发限制
 * 2. 单个任务特定的并发限制
 * 3. 先获取全局许可，再获取任务级许可
 * 4. 释放时先释放任务级，再释放全局
 */
@DisplayName("TaskConcurrencyLimiter - TDD")
class TaskConcurrencyLimiterTddTest {

    // ==================== Red Phase: 编写测试 ====================

    @Test
    @DisplayName("1. [spec.md] 应支持全局最大并发限制")
    void testGlobalMaxLimit() {
        // Given: 全局最大并发=2
        TaskConcurrencyLimiter limiter = new TaskConcurrencyLimiter(2, 10);

        // When: 获取2个许可
        boolean acquired1 = limiter.tryAcquire("task-1");
        boolean acquired2 = limiter.tryAcquire("task-2");

        // Then: 都应成功
        assertTrue(acquired1, "第一个许可应获取成功");
        assertTrue(acquired2, "第二个许可应获取成功");

        // When: 获取第3个许可
        boolean acquired3 = limiter.tryAcquire("task-3");

        // Then: 应失败（全局已满）
        assertFalse(acquired3, "全局已满，第3个许可应失败");

        System.out.println("✅ 全局并发限制验证: max=2, 第3个获取失败");
    }

    @Test
    @DisplayName("2. [spec.md] 应支持任务级并发限制")
    void testTaskLevelLimit() {
        // Given: 全局最大=10，任务task-1限制=1
        TaskConcurrencyLimiter limiter = new TaskConcurrencyLimiter(10, 10);
        limiter.setTaskMax("task-1", 1);

        // When: 获取task-1的第一个许可
        boolean acquired1 = limiter.tryAcquire("task-1");

        // Then: 应成功
        assertTrue(acquired1, "task-1第一个许可应成功");

        // When: 获取task-1的第二个许可
        boolean acquired2 = limiter.tryAcquire("task-1");

        // Then: 应失败（任务级已满）
        assertFalse(acquired2, "task-1限制=1，第二个应失败");

        System.out.println("✅ 任务级并发限制验证: task-1 max=1, 第2个获取失败");
    }

    @Test
    @DisplayName("3. [spec.md] 不同任务应独立计数")
    void testDifferentTasksIndependent() {
        // Given: 全局最大=10，task-1限制=1，task-2限制=1
        TaskConcurrencyLimiter limiter = new TaskConcurrencyLimiter(10, 10);
        limiter.setTaskMax("task-1", 1);
        limiter.setTaskMax("task-2", 1);

        // When: 各获取1个许可
        boolean acquired1 = limiter.tryAcquire("task-1");
        boolean acquired2 = limiter.tryAcquire("task-2");

        // Then: 都应成功
        assertTrue(acquired1, "task-1应成功");
        assertTrue(acquired2, "task-2应成功");

        System.out.println("✅ 任务隔离验证: task-1和task-2各自独立计数");
    }

    @Test
    @DisplayName("4. [spec.md] 释放后应允许重新获取")
    void testReleaseAndReacquire() {
        // Given: 全局最大=1
        TaskConcurrencyLimiter limiter = new TaskConcurrencyLimiter(1, 10);

        // When: 获取并释放
        limiter.tryAcquire("task-1");
        limiter.release("task-1");

        // Then: 应能重新获取
        boolean reacquired = limiter.tryAcquire("task-2");
        assertTrue(reacquired, "释放后应能重新获取");

        System.out.println("✅ 释放重获取验证: 释放后可重新获取");
    }

    @Test
    @DisplayName("5. [spec.md] 无任务级限制时应只受全局限制")
    void testNoTaskLimit() {
        // Given: 全局最大=2，无任务级限制
        TaskConcurrencyLimiter limiter = new TaskConcurrencyLimiter(2, 10);

        // When: 同一任务获取2个许可
        boolean acquired1 = limiter.tryAcquire("task-1");
        boolean acquired2 = limiter.tryAcquire("task-1");

        // Then: 都应成功（只受全局限制）
        assertTrue(acquired1, "第一个应成功");
        assertTrue(acquired2, "第二个应成功（无任务级限制）");

        // When: 获取第3个
        boolean acquired3 = limiter.tryAcquire("task-1");
        assertFalse(acquired3, "全局已满，第3个应失败");

        System.out.println("✅ 无任务级限制验证: 只受全局限制");
    }

    @Test
    @DisplayName("6. [spec.md] 并发场景下应正确限制")
    void testConcurrentAcquire() throws InterruptedException {
        // Given: 全局最大=2
        TaskConcurrencyLimiter limiter = new TaskConcurrencyLimiter(2, 10);
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(5);

        // When: 5个线程同时获取
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            new Thread(() -> {
                if (limiter.tryAcquire("task-" + idx)) {
                    successCount.incrementAndGet();
                }
                latch.countDown();
            }).start();
        }

        // Then: 最多2个成功
        assertTrue(latch.await(2, TimeUnit.SECONDS), "所有线程应完成");
        assertEquals(2, successCount.get(), "全局max=2，应只有2个成功");

        System.out.println("✅ 并发限制验证: 5个线程中" + successCount.get() + "个成功");
    }

    @Test
    @DisplayName("7. [spec.md] 应支持获取当前可用许可数")
    void testGetAvailablePermits() {
        // Given: 全局最大=3
        TaskConcurrencyLimiter limiter = new TaskConcurrencyLimiter(3, 10);

        // Then: 初始应有3个可用
        assertEquals(3, limiter.getGlobalAvailablePermits(), "初始应有3个可用");

        // When: 获取1个
        limiter.tryAcquire("task-1");

        // Then: 应有2个可用
        assertEquals(2, limiter.getGlobalAvailablePermits(), "获取1个后应有2个可用");

        System.out.println("✅ 可用许可数验证: 初始3，获取1后剩2");
    }

    @Test
    @DisplayName("8. [spec.md] 修改任务限制后应生效")
    void testSetTaskMax() {
        // Given: 全局最大=10，task-1初始无限制
        TaskConcurrencyLimiter limiter = new TaskConcurrencyLimiter(10, 10);

        // When: 设置task-1限制=1
        limiter.setTaskMax("task-1", 1);
        limiter.tryAcquire("task-1");

        // Then: 第二个应失败
        boolean acquired2 = limiter.tryAcquire("task-1");
        assertFalse(acquired2, "设置限制后第二个应失败");

        System.out.println("✅ 动态设置限制验证: setTaskMax后生效");
    }
}
