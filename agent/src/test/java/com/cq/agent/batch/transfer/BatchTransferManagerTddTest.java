package com.cq.agent.batch.transfer;

import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD测试：验证BatchTransferManager集成TaskConcurrencyLimiter
 * 核心要求（spec.md）：
 * 1. 支持全局并发限制
 * 2. 支持任务级并发限制
 * 3. 任务级限制优先于全局限制
 */
@DisplayName("BatchTransferManager - TDD集成TaskConcurrencyLimiter")
class BatchTransferManagerTddTest {

    // ==================== Red Phase: 编写测试 ====================

    @Test
    @DisplayName("1. [spec.md] 应支持全局并发获取")
    void testAcquireGlobal() {
        // Given: 全局最大=2
        BatchTransferManager manager = new BatchTransferManager(2);

        // When: 获取2个许可
        boolean acquired1 = manager.acquire();
        boolean acquired2 = manager.acquire();

        // Then: 都应成功
        assertTrue(acquired1, "第一个应成功");
        assertTrue(acquired2, "第二个应成功");

        System.out.println("✅ 全局并发获取验证: max=2, 2个都成功");
    }

    @Test
    @DisplayName("2. [spec.md] 全局满时应阻塞或失败")
    void testAcquireGlobalFull() {
        // Given: 全局最大=1
        BatchTransferManager manager = new BatchTransferManager(1);
        manager.acquire(); // 占用唯一许可

        // When: 尝试再获取（不阻塞，用tryAcquire）
        boolean acquired = manager.tryAcquire(100, TimeUnit.MILLISECONDS);

        // Then: 应失败
        assertFalse(acquired, "全局已满，应失败");

        System.out.println("✅ 全局满验证: max=1, 第二个获取失败");
    }

    @Test
    @DisplayName("3. [spec.md] 释放后应可重新获取")
    void testRelease() {
        // Given: 全局最大=1
        BatchTransferManager manager = new BatchTransferManager(1);
        manager.acquire();

        // When: 释放并重新获取
        manager.release();
        boolean reacquired = manager.acquire();

        // Then: 应成功
        assertTrue(reacquired, "释放后应能重新获取");

        System.out.println("✅ 释放验证: 释放后可重新获取");
    }

    @Test
    @DisplayName("4. [spec.md] 应支持任务级并发限制")
    void testTaskLevelLimit() {
        // Given: 全局最大=10，任务task-1限制=1
        BatchTransferManager manager = new BatchTransferManager(10);
        manager.setTaskMax("task-1", 1);

        // When: 获取task-1的第一个许可
        boolean acquired1 = manager.tryAcquire("task-1");

        // Then: 应成功
        assertTrue(acquired1, "task-1第一个应成功");

        // When: 获取task-1的第二个许可
        boolean acquired2 = manager.tryAcquire("task-1");

        // Then: 应失败（任务级已满）
        assertFalse(acquired2, "task-1限制=1，第二个应失败");

        System.out.println("✅ 任务级限制验证: task-1 max=1, 第2个失败");
    }

    @Test
    @DisplayName("5. [spec.md] 不同任务应独立计数")
    void testDifferentTasks() {
        // Given: 全局最大=10，task-1限制=1，task-2限制=1
        BatchTransferManager manager = new BatchTransferManager(10);
        manager.setTaskMax("task-1", 1);
        manager.setTaskMax("task-2", 1);

        // When: 各获取1个
        boolean acquired1 = manager.tryAcquire("task-1");
        boolean acquired2 = manager.tryAcquire("task-2");

        // Then: 都应成功
        assertTrue(acquired1, "task-1应成功");
        assertTrue(acquired2, "task-2应成功");

        System.out.println("✅ 任务隔离验证: task-1和task-2各自独立");
    }

    @Test
    @DisplayName("6. [spec.md] 无任务级限制时应只受全局限制")
    void testNoTaskLimit() {
        // Given: 全局最大=2，无任务级限制
        BatchTransferManager manager = new BatchTransferManager(2);

        // When: 同一任务获取2个
        boolean acquired1 = manager.tryAcquire("task-1");
        boolean acquired2 = manager.tryAcquire("task-1");

        // Then: 都应成功
        assertTrue(acquired1, "第一个应成功");
        assertTrue(acquired2, "第二个应成功（无任务级限制）");

        // When: 获取第3个
        boolean acquired3 = manager.tryAcquire("task-1");
        assertFalse(acquired3, "全局已满，第3个应失败");

        System.out.println("✅ 无任务级限制验证: 只受全局限制");
    }

    @Test
    @DisplayName("7. [spec.md] 并发场景下应正确限制")
    void testConcurrentLimit() throws InterruptedException {
        // Given: 全局最大=2
        BatchTransferManager manager = new BatchTransferManager(2);
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(5);

        // When: 5个线程同时获取
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            new Thread(() -> {
                if (manager.tryAcquire("task-" + idx)) {
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
    @DisplayName("8. [spec.md] 应支持获取可用许可数")
    void testGetAvailablePermits() {
        // Given: 全局最大=3
        BatchTransferManager manager = new BatchTransferManager(3);

        // Then: 初始应有3个
        assertEquals(3, manager.getAvailablePermits(), "初始应有3个");

        // When: 获取1个
        manager.acquire();

        // Then: 应有2个
        assertEquals(2, manager.getAvailablePermits(), "获取1个后应有2个");

        System.out.println("✅ 可用许可数验证: 初始3，获取1后剩2");
    }
}
