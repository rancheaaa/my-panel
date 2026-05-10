package com.cq.agent.batch.transfer;

import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全局并发控制单元测试
 * 覆盖率目标：100%
 */
@DisplayName("全局并发控制 - BatchTransferManager")
class BatchTransferManagerTest {

    private BatchTransferManager batchTransferManager;

    @BeforeEach
    void setUp() {
        batchTransferManager = new BatchTransferManager(5);
    }

    // ==================== 1. 获取许可 - 在限制内 ====================

    @Test
    @DisplayName("1. 获取许可 - 在限制内成功")
    void testAcquirePermit_withinLimit() throws Exception {
        boolean acquired = batchTransferManager.acquire();
        
        assertTrue(acquired, "应在限制内获取到许可");
        assertEquals(4, batchTransferManager.getAvailablePermits(), "剩余许可应减1");
        
        System.out.println("✅ 获取许可成功: remaining=4");
    }

    // ==================== 2. 获取许可 - 超过限制阻塞 ====================

    @Test
    @DisplayName("2. 获取许可 - 超过限制后阻塞")
    void testAcquirePermit_exceedLimit_blocks() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertTrue(batchTransferManager.acquire(), "前5次应成功");
        }
        
        AtomicBoolean blocked = new AtomicBoolean(false);
        Thread acquireThread = new Thread(() -> {
            try {
                batchTransferManager.acquire();
                blocked.set(false); // 如果执行到这里说明没阻塞
            } catch (Exception e) {
                blocked.set(true);
            }
        });
        
        acquireThread.start();
        Thread.sleep(500);
        
        assertTrue(acquireThread.isAlive() || blocked.get(), 
            "超过限制应阻塞或等待");
            
        acquireThread.interrupt();
        acquireThread.join(1000);
        
        System.out.println("✅ 超过限制阻塞验证通过");
    }

    // ==================== 3. 释放许可 ====================

    @Test
    @DisplayName("3. 释放许可 - 计数增加")
    void testReleasePermit_countDecremented() {
        batchTransferManager.acquire();
        batchTransferManager.acquire();
        assertEquals(3, batchTransferManager.getAvailablePermits());
        
        batchTransferManager.release();
        assertEquals(4, batchTransferManager.getAvailablePermits());
        
        System.out.println("✅ 释放许可成功: available=4");
    }

    // ==================== 4. 尝试获取 - 超时返回false ====================

    @Test
    @DisplayName("4. 尝试获取 - 超时返回false")
    void testTryAcquire_timeout() throws Exception {
        for (int i = 0; i < 5; i++) {
            batchTransferManager.acquire();
        }
        
        long startTime = System.currentTimeMillis();
        boolean acquired = batchTransferManager.tryAcquire(200, TimeUnit.MILLISECONDS);
        long duration = System.currentTimeMillis() - startTime;
        
        assertFalse(acquired, "超时后应返回false");
        assertTrue(duration >= 180, "等待时间应接近超时时间");
        
        System.out.println("✅ 尝试获取超时: duration=" + duration + "ms");
    }

    // ==================== 5. 公平性 - 无饥饿 ====================

    @Test
    @DisplayName("5. 公平性 - 多线程竞争无饥饿")
    void testFairness_noStarvation() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.countDown();
                    startLatch.await(); // 所有线程同时开始
                    
                    if (batchTransferManager.tryAcquire(2, TimeUnit.SECONDS)) {
                        successCount.incrementAndGet();
                        Thread.sleep(100); // 模拟工作
                        batchTransferManager.release();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }
        
        doneLatch.await(10, TimeUnit.SECONDS);
        
        assertTrue(successCount.get() > 0, "应有部分线程获取到许可");
        assertTrue(successCount.get() <= threadCount, "成功数不应超过线程总数");
        
        System.out.println("✅ 公平性测试: threads=" + threadCount + 
                          ", success=" + successCount.get());
    }

    // ==================== 6. 监控可用许可 ====================

    @Test
    @DisplayName("6. 可用许可监控")
    void testAvailablePermits_monitoring() {
        assertEquals(5, batchTransferManager.getAvailablePermits(), "初始应为5");
        
        batchTransferManager.acquire();
        assertEquals(4, batchTransferManager.getAvailablePermits());
        
        batchTransferManager.acquire();
        batchTransferManager.acquire();
        assertEquals(2, batchTransferManager.getAvailablePermits());
        
        batchTransferManager.release();
        batchTransferManager.release();
        assertEquals(4, batchTransferManager.getAvailablePermits());
        
        System.out.println("✅ 许可监控正常: current=" + 
                          batchTransferManager.getAvailablePermits());
    }
}
