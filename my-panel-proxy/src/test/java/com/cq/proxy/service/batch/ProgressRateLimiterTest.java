package com.cq.proxy.service.batch;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 限流保护单元测试
 * 覆盖率目标：100%
 */
@DisplayName("限流保护 - ProgressRateLimiter")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProgressRateLimiterTest {

    private ProgressRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new ProgressRateLimiter();
    }

    @AfterEach
    void tearDown() {
        rateLimiter.shutdown();
    }

    // ==================== 1. 基础限流功能 ====================

    @Test
    @Order(1)
    @DisplayName("1. tryAcquire() 正常通过")
    void testTryAcquire_normalPass() {
        assertTrue(rateLimiter.tryAcquire());
        System.out.println("✅ 正常请求通过");
    }

    // ==================== 2. 超过阈值拒绝 ====================

    @Test
    @Order(2)
    @DisplayName("2. 超过QPS阈值被限流")
    void testTryAcquire_rateLimited() throws InterruptedException {
        int acquired = 0;
        int rejected = 0;
        
        for (int i = 0; i < ProgressRateLimiter.MAX_QPS + 10; i++) {
            if (rateLimiter.tryAcquire()) {
                acquired++;
            } else {
                rejected++;
            }
        }
        
        System.out.println("✅ QPS限流: acquired=" + acquired + ", rejected=" + rejected);
        assertTrue(rejected > 0, "应该有请求被限流");
        assertEquals(ProgressRateLimiter.MAX_QPS, acquired);
    }

    // ==================== 3. 窗口重置机制 ====================

    @Test
    @Order(3)
    @DisplayName("3. 时间窗口到期后计数器重置")
    void testWindowReset_afterInterval() throws InterruptedException {
        for (int i = 0; i < ProgressRateLimiter.MAX_QPS; i++) {
            assertTrue(rateLimiter.tryAcquire());
        }
        
        assertFalse(rateLimiter.tryAcquire(), "窗口内应被限流");
        
        Thread.sleep(1100); // 等待窗口重置 (>1000ms)
        
        assertTrue(rateLimiter.tryAcquire(), "新窗口应允许通过");
        System.out.println("✅ 窗口重置成功");
    }

    // ==================== 4. 并发安全测试 ====================

    @Test
    @Order(4)
    @DisplayName("4. 高并发场景下限流准确")
    void testConcurrentRequests_accurateLimiting() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(20);
        AtomicInteger passedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(200);
        
        for (int i = 0; i < 200; i++) {
            executor.submit(() -> {
                try {
                    if (rateLimiter.tryAcquire()) {
                        passedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        int total = passedCount.get() + rejectedCount.get();
        System.out.println("✅ 并发限流: passed=" + passedCount.get() + 
                          ", rejected=" + rejectedCount.get() + ", total=" + total);
        
        assertEquals(200, total, "所有请求都应处理完毕");
        assertTrue(passedCount.get() <= ProgressRateLimiter.MAX_QPS * 2, 
                  "通过的请求数应在合理范围内");
    }

    // ==================== 5. shutdown清理 ====================

    @Test
    @Order(5)
    @DisplayName("5. shutdown()后停止服务")
    void testShutdown_stopsService() {
        rateLimiter.shutdown();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            rateLimiter.tryAcquire();
        });
        
        assertEquals("Rate limiter已关闭", exception.getMessage());
        System.out.println("✅ shutdown后正确抛出异常");
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("6. 连续快速请求边界测试")
    void testRapidRequests_boundaryBehavior() throws InterruptedException {
        int successInFirstWindow = 0;
        
        for (int i = 0; i < ProgressRateLimiter.MAX_QPS * 2; i++) {
            if (rateLimiter.tryAcquire()) {
                successInFirstWindow++;
            }
        }
        
        assertEquals(ProgressRateLimiter.MAX_QPS, successInFirstWindow,
            "第一个窗口最多允许MAX_QPS个请求");
        System.out.println("✅ 边界测试: 第一个窗口成功=" + successInFirstWindow);
    }

    // ==================== 辅助方法（可选）====================
    
    private void waitForNextWindow() throws InterruptedException {
        Thread.sleep(1100); // 等待超过1秒窗口
    }
}
