package com.cq.agent.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TrafficRateLimiter 单元测试
 * 基于 Guava RateLimiter SmoothBursty 算法的流量限流器测试
 *
 * Guava SmoothBursty 核心行为：
 * 1. 第一个请求总是立即通过（等待 nextFreeTicketMicros - nowMicros，初始为0）
 * 2. 当前请求的等待时间 = nextFreeTicketMicros - nowMicros（之前请求预支的时间）
 * 3. 当前请求的 fresh permits 成本会推进 nextFreeTicketMicros，转嫁给下一个请求
 * 4. storedPermits 的消耗不产生等待时间（storedPermitsToWaitTime = 0），允许突发
 * 5. 空闲时积累 storedPermits（最多 maxBurstSeconds 秒的量）
 */
class TrafficRateLimiterTest {

    private static final double DELTA = 0.01;

    @Test
    @DisplayName("构造函数 - 正常速率")
    void testConstructorNormalRate() {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1024);
        assertEquals(1024.0, limiter.getRate(), DELTA);
    }

    @Test
    @DisplayName("构造函数 - 零速率抛异常")
    void testConstructorZeroRate() {
        assertThrows(IllegalArgumentException.class, () -> new TrafficRateLimiter(0));
    }

    @Test
    @DisplayName("构造函数 - 负速率抛异常")
    void testConstructorNegativeRate() {
        assertThrows(IllegalArgumentException.class, () -> new TrafficRateLimiter(-1));
    }

    @Test
    @DisplayName("acquire - 零字节不阻塞")
    void testAcquireZeroBytes() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);
        long start = System.nanoTime();
        limiter.acquire(0, "trace1");
        long elapsed = System.nanoTime() - start;
        assertTrue(elapsed < TimeUnit.MILLISECONDS.toNanos(10),
                "acquire(0) should not block");
    }

    @Test
    @DisplayName("acquire - 负数字节不阻塞")
    void testAcquireNegativeBytes() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);
        long start = System.nanoTime();
        limiter.acquire(-1, "trace1");
        long elapsed = System.nanoTime() - start;
        assertTrue(elapsed < TimeUnit.MILLISECONDS.toNanos(10));
    }

    @Test
    @DisplayName("acquire - 单次请求不阻塞（第一个请求立即通过）")
    void testAcquireFirstRequestNotBlocked() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);
        long start = System.nanoTime();
        limiter.acquire(100, "trace1");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsedMs < 50,
                "First request should not be blocked, elapsed: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("acquire - 连续请求被限流")
    void testAcquireConsecutiveRequestsThrottled() throws InterruptedException {
        // 速率 1000 bytes/s，即 1ms/byte
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        // 第一个请求：acquire(100)，立即通过，但 nextFreeTicketMicros 推进 100ms
        limiter.acquire(100, "trace1");

        // 第二个请求：acquire(100)，需要等待约 100ms
        long start = System.nanoTime();
        limiter.acquire(100, "trace2");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // 应该等待约 100ms（允许误差）
        assertTrue(elapsedMs >= 50 && elapsedMs < 200,
                "Second request should wait ~100ms, actual: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("acquire - 大请求预支未来时间")
    void testAcquireLargeRequestBorrowsFromFuture() throws InterruptedException {
        // 速率 1000 bytes/s
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        // acquire(500)：第一个请求立即通过，但 nextFreeTicketMicros 推进 500ms
        long start = System.nanoTime();
        limiter.acquire(500, "trace1");
        long elapsed1 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed1 < 50, "First request should be immediate, elapsed: " + elapsed1 + "ms");

        // acquire(100)：需要等待约 500ms（因为上一个请求预支了 500ms）
        start = System.nanoTime();
        limiter.acquire(100, "trace2");
        long elapsed2 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed2 >= 400 && elapsed2 < 700,
                "Second request should wait ~500ms, actual: " + elapsed2 + "ms");
    }

    @Test
    @DisplayName("acquire - 空闲后允许突发（SmoothBursty 核心特性）")
    void testAcquireBurstAfterIdle() throws InterruptedException {
        // 速率 1000 bytes/s，maxBurstSeconds=1.0，所以 maxPermits=1000
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        // 空闲 1.1 秒，积累约 1000 storedPermits
        Thread.sleep(1100);

        // acquire(500)：从 storedPermits 中消耗 500，无需等待
        // SmoothBursty 中 storedPermitsToWaitTime = 0，不推进 nextFreeTicketMicros
        long start = System.nanoTime();
        limiter.acquire(500, "trace1");
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed < 50, "Burst after idle should be immediate, elapsed: " + elapsed + "ms");

        // acquire(600)：剩余 500 storedPermits + 100 fresh permits
        // storedPermits 部分无需等待，fresh permits 推进 nextFreeTicketMicros 100ms
        // 但当前请求的等待时间 = nextFreeTicketMicros - nowMicros
        // 由于 resync 后 nextFreeTicketMicros = nowMicros，所以当前请求也立即通过！
        // 这就是 SmoothBursty 的"突发"特性：空闲后可以连续突发
        start = System.nanoTime();
        limiter.acquire(600, "trace2");
        long elapsed2 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // 当前请求立即通过（nextFreeTicketMicros 在过去或现在）
        // 但 fresh permits 的 100ms 成本转嫁给下一个请求
        assertTrue(elapsed2 < 100,
                "SmoothBursty allows burst, current request should be fast, actual: " + elapsed2 + "ms");

        // acquire(100)：需要为上一个请求的 fresh permits 买单
        start = System.nanoTime();
        limiter.acquire(100, "trace3");
        long elapsed3 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // 需要等待约 100ms（上一个请求的 fresh permits 成本）+ 100ms（本次 fresh permits）
        assertTrue(elapsed3 >= 50,
                "After burst, next request pays for fresh permits, elapsed: " + elapsed3 + "ms");
    }

    @Test
    @DisplayName("acquire - 精确速率验证")
    void testAcquireRateAccuracy() throws InterruptedException {
        // 速率 5000 bytes/s，即 0.2ms/byte
        TrafficRateLimiter limiter = new TrafficRateLimiter(5000);

        // 连续 acquire 10 次，每次 500 bytes
        long start = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            limiter.acquire(500, "trace" + i);
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // 理论上需要约 900ms（第一个立即通过，后续9个各等100ms）
        assertTrue(elapsedMs >= 700 && elapsedMs < 1500,
                "10x500 bytes at 5000 bytes/s should take ~900ms, actual: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("tryAcquire - 超时内可获取")
    void testTryAcquireWithinTimeout() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        limiter.acquire(100, "trace1");

        boolean result = limiter.tryAcquire(100, 500, TimeUnit.MILLISECONDS, "trace2");
        assertTrue(result, "tryAcquire should succeed within timeout");
    }

    @Test
    @DisplayName("tryAcquire - 超时不足返回false")
    void testTryAcquireTimeoutTooShort() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        // acquire(1000)：推进 nextFreeTicketMicros 1000ms
        limiter.acquire(1000, "trace1");

        // tryAcquire 超时 50ms，但需要等约 1000ms，应该返回 false
        boolean result = limiter.tryAcquire(100, 50, TimeUnit.MILLISECONDS, "trace2");
        assertFalse(result, "tryAcquire should fail when timeout is too short");
    }

    @Test
    @DisplayName("tryAcquire - 零超时")
    void testTryAcquireZeroTimeout() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        limiter.acquire(100, "trace1");

        boolean result = limiter.tryAcquire(100, 0, TimeUnit.MILLISECONDS, "trace2");
        assertFalse(result, "tryAcquire with 0 timeout should fail when next ticket is in future");
    }

    @Test
    @DisplayName("tryAcquire - 关闭后返回true")
    void testTryAcquireAfterShutdown() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);
        limiter.shutdown();
        boolean result = limiter.tryAcquire(100, 100, TimeUnit.MILLISECONDS, "trace1");
        assertTrue(result, "tryAcquire after shutdown should return true");
    }

    @Test
    @DisplayName("shutdown - 关闭后acquire不阻塞")
    void testAcquireAfterShutdown() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);
        limiter.shutdown();
        long start = System.nanoTime();
        limiter.acquire(10000, "trace1");
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed < 10, "acquire after shutdown should not block");
    }

    @Test
    @DisplayName("setRate - 动态更新速率")
    void testSetRate() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);
        assertEquals(1000.0, limiter.getRate(), DELTA);

        limiter.setRate(2000);
        assertEquals(2000.0, limiter.getRate(), DELTA);
    }

    @Test
    @DisplayName("setRate - 零速率抛异常")
    void testSetRateZero() {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);
        assertThrows(IllegalArgumentException.class, () -> limiter.setRate(0));
    }

    @Test
    @DisplayName("setRate - 负速率抛异常")
    void testSetRateNegative() {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);
        assertThrows(IllegalArgumentException.class, () -> limiter.setRate(-1));
    }

    @Test
    @DisplayName("setRate - 更新速率后限流生效")
    void testSetRateAffectsThrottling() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        // 先消耗掉 storedPermits 并推进 nextFreeTicketMicros
        limiter.acquire(1000, "trace1"); // nextFreeTicketMicros 推进 1000ms

        // 等待 nextFreeTicketMicros 到来，但不要等太久（避免积累 storedPermits）
        Thread.sleep(1100);

        // 更新速率为 2000 bytes/s
        limiter.setRate(2000);

        // 此时 resync 会积累约 100 storedPermits（100ms 的差值 × 1000 bytes/s）
        // 但 setRate 后 maxPermits = 2000，storedPermits 被按比例调整
        // 先消耗掉 storedPermits
        limiter.acquire(2000, "trace_consume"); // 消耗所有 storedPermits + fresh permits

        // 现在 storedPermits = 0，nextFreeTicketMicros 在未来
        // acquire(200)：全部需要 fresh permits，在 2000 bytes/s 下需要 100ms
        long start = System.nanoTime();
        limiter.acquire(200, "trace2");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // 应该等待约 1000ms（上一个请求预支的时间）
        assertTrue(elapsedMs >= 500,
                "After setRate, should wait for previous fresh permits, elapsed: " + elapsedMs + "ms");

        // 验证新速率：连续请求间隔应该更短
        // acquire(200)：在 2000 bytes/s 下需要 100ms
        start = System.nanoTime();
        limiter.acquire(200, "trace3");
        long elapsed2 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // 应该等待约 100ms（200 bytes at 2000 bytes/s）
        assertTrue(elapsed2 >= 50 && elapsed2 < 300,
                "At 2000 bytes/s, 200 bytes should wait ~100ms, elapsed: " + elapsed2 + "ms");
    }

    @Test
    @DisplayName("并发安全 - 多线程acquire")
    void testConcurrentAcquire() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(10000);
        int threadCount = 5;
        int requestsPerThread = 10;
        int bytesPerRequest = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < requestsPerThread; i++) {
                        limiter.acquire(bytesPerRequest, "t" + threadId + "-r" + i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long startTime = System.nanoTime();
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        long totalElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        executor.shutdown();

        // 总传输量 = 5 * 10 * 100 = 5000 bytes
        // 在 10000 bytes/s 下，理论上需要约 500ms
        assertTrue(totalElapsedMs < 5000,
                "Concurrent acquire should complete within reasonable time, actual: " + totalElapsedMs + "ms");
    }

    @Test
    @DisplayName("并发安全 - acquire和tryAcquire混合")
    void testConcurrentAcquireAndTryAcquire() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(5000);
        int threadCount = 3;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount * 2);
        AtomicInteger tryAcquireSuccess = new AtomicInteger(0);
        AtomicInteger tryAcquireFail = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount * 2);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 5; i++) {
                        limiter.acquire(100, "acquire-t" + threadId + "-r" + i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 5; i++) {
                        boolean result = limiter.tryAcquire(100, 200, TimeUnit.MILLISECONDS,
                                "tryAcquire-t" + threadId + "-r" + i);
                        if (result) {
                            tryAcquireSuccess.incrementAndGet();
                        } else {
                            tryAcquireFail.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        int total = tryAcquireSuccess.get() + tryAcquireFail.get();
        assertEquals(15, total, "All tryAcquire calls should complete");
    }

    @Test
    @DisplayName("storedPermits 积累 - 空闲时间转化为许可")
    void testStoredPermitsAccumulation() throws InterruptedException {
        // 速率 1000 bytes/s，maxBurstSeconds=1.0，maxPermits=1000
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        // 空闲 1.2 秒，应该积累约 1000 storedPermits（最多 1000）
        Thread.sleep(1200);

        // acquire(800)：从 storedPermits 中消耗 800，无需等待
        long start = System.nanoTime();
        limiter.acquire(800, "trace1");
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed < 50, "Should use storedPermits without waiting, elapsed: " + elapsed + "ms");

        // acquire(300)：剩余 200 storedPermits + 100 fresh permits
        // SmoothBursty 中 storedPermits 不推进 nextFreeTicketMicros
        // resync 后 nextFreeTicketMicros = nowMicros，所以当前请求立即通过
        start = System.nanoTime();
        limiter.acquire(300, "trace2");
        long elapsed2 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // 当前请求立即通过，但 100ms 的 fresh permits 成本转嫁给下一个请求
        assertTrue(elapsed2 < 100,
                "SmoothBursty: storedPermits + fresh permits, current request should be fast, elapsed: " + elapsed2 + "ms");

        // acquire(100)：需要为上一个请求的 fresh permits 买单
        start = System.nanoTime();
        limiter.acquire(100, "trace3");
        long elapsed3 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed3 >= 50,
                "Should wait for previous fresh permits, elapsed: " + elapsed3 + "ms");
    }

    @Test
    @DisplayName("maxBurstSeconds 限制 - 突发上限")
    void testMaxBurstLimit() throws InterruptedException {
        // 速率 1000 bytes/s，maxBurstSeconds=1.0，maxPermits=1000
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        // 空闲 5 秒，但最多只能积累 1000 storedPermits（1秒的量）
        Thread.sleep(5000);

        // acquire(1000)：从 storedPermits 消耗 1000，无需等待
        long start = System.nanoTime();
        limiter.acquire(1000, "trace1");
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed < 50, "Max burst should be 1000 bytes, elapsed: " + elapsed + "ms");

        // acquire(100)：storedPermits 已用完，全部需要 fresh permits
        // 但 resync 后 nextFreeTicketMicros = nowMicros，当前请求立即通过
        // fresh permits 的 100ms 成本转嫁给下一个请求
        start = System.nanoTime();
        limiter.acquire(100, "trace2");
        long elapsed2 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed2 < 100,
                "SmoothBursty: current request passes immediately, elapsed: " + elapsed2 + "ms");

        // acquire(100)：需要为上一个请求的 fresh permits 买单
        start = System.nanoTime();
        limiter.acquire(100, "trace3");
        long elapsed3 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed3 >= 50,
                "After max burst, should wait for fresh permits, elapsed: " + elapsed3 + "ms");
    }

    @Test
    @DisplayName("高速率 - 大字节每秒")
    void testHighRate() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1048576);
        assertEquals(1048576.0, limiter.getRate(), 1.0);

        long start = System.nanoTime();
        limiter.acquire(1024, "trace1");
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed < 50, "First request at high rate should be immediate");

        start = System.nanoTime();
        limiter.acquire(1024, "trace2");
        elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed < 100, "Second request at high rate should wait ~1ms");
    }

    @Test
    @DisplayName("低速率 - 小字节每秒")
    void testLowRate() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(100);

        limiter.acquire(50, "trace1");

        long start = System.nanoTime();
        limiter.acquire(50, "trace2");
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed >= 300 && elapsed < 800,
                "At 100 bytes/s, 50 bytes should wait ~500ms, actual: " + elapsed + "ms");
    }

    @Test
    @DisplayName("acquire(1) - 最小单位")
    void testAcquireSingleByte() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        limiter.acquire(1, "trace1");
        long start = System.nanoTime();
        for (int i = 2; i <= 5; i++) {
            limiter.acquire(1, "trace" + i);
        }
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed < 100, "4x acquire(1) at 1000 bytes/s should take ~4ms, actual: " + elapsed + "ms");
    }

    @Test
    @DisplayName("InterruptedException - acquire可被中断")
    void testAcquireInterruptible() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(100);

        // 先推进 nextFreeTicketMicros 到很远的未来
        limiter.acquire(10000, "trace1");

        Thread t = new Thread(() -> {
            try {
                limiter.acquire(1, "trace2");
                fail("Should have been interrupted");
            } catch (InterruptedException e) {
                // 预期被中断
            }
        });
        t.start();
        Thread.sleep(50);
        t.interrupt();
        t.join(1000);
        assertTrue(t.isInterrupted() || !t.isAlive(), "Thread should be interrupted");
    }

    @Test
    @DisplayName("速率精度 - getRate返回正确值")
    void testGetRateAccuracy() {
        TrafficRateLimiter limiter = new TrafficRateLimiter(512);
        assertEquals(512.0, limiter.getRate(), DELTA);

        limiter.setRate(2048);
        assertEquals(2048.0, limiter.getRate(), DELTA);

        limiter.setRate(1);
        assertEquals(1.0, limiter.getRate(), DELTA);
    }

    @Test
    @DisplayName("多次setRate - 速率切换")
    void testMultipleSetRate() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        limiter.setRate(500);
        assertEquals(500.0, limiter.getRate(), DELTA);

        limiter.setRate(2000);
        assertEquals(2000.0, limiter.getRate(), DELTA);

        limiter.setRate(100);
        assertEquals(100.0, limiter.getRate(), DELTA);
    }

    @Test
    @DisplayName("边界 - 速率1 byte/s")
    void testRateOneBytePerSecond() {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1);
        assertEquals(1.0, limiter.getRate(), DELTA);
    }

    @Test
    @DisplayName("边界 - 大速率 Integer.MAX_VALUE")
    void testLargeRate() {
        TrafficRateLimiter limiter = new TrafficRateLimiter(Integer.MAX_VALUE);
        assertEquals((double) Integer.MAX_VALUE, limiter.getRate(), 1.0);
    }

    @Test
    @DisplayName("tryAcquire - 零字节返回true")
    void testTryAcquireZeroBytes() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);
        assertTrue(limiter.tryAcquire(0, 0, TimeUnit.MILLISECONDS, "trace1"));
    }

    @Test
    @DisplayName("tryAcquire - 负数字节返回true")
    void testTryAcquireNegativeBytes() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);
        assertTrue(limiter.tryAcquire(-1, 0, TimeUnit.MILLISECONDS, "trace1"));
    }

    @Test
    @DisplayName("空闲后连续小请求 - 验证平滑限流")
    void testSmoothThrottlingAfterIdle() throws InterruptedException {
        // 速率 1000 bytes/s
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        // 空闲一段时间，积累 storedPermits
        Thread.sleep(500);

        // 消耗掉所有 storedPermits（500 bytes）
        limiter.acquire(500, "trace1");

        // acquire(100)：storedPermits 已用完，需要 fresh permits
        // 但 resync 后 nextFreeTicketMicros = nowMicros，当前请求立即通过
        // fresh permits 的 100ms 成本转嫁给下一个请求
        long start = System.nanoTime();
        limiter.acquire(100, "trace2");
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // SmoothBursty: 当前请求立即通过（nextFreeTicketMicros 在过去或现在）
        assertTrue(elapsed < 100,
                "SmoothBursty: current request should be fast, elapsed: " + elapsed + "ms");

        // acquire(100)：需要为上一个请求的 fresh permits 买单
        start = System.nanoTime();
        limiter.acquire(100, "trace3");
        long elapsed2 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed2 >= 50,
                "Should wait for previous fresh permits, elapsed: " + elapsed2 + "ms");
    }

    @Test
    @DisplayName("连续请求 - 验证预支未来时间机制")
    void testBorrowFromFutureMechanism() throws InterruptedException {
        // 速率 1000 bytes/s
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        // acquire(100)：立即通过，nextFreeTicketMicros 推进 100ms
        limiter.acquire(100, "trace1");

        // acquire(100)：等待约 100ms（上一个请求预支的时间），nextFreeTicketMicros 再推进 100ms
        long start = System.nanoTime();
        limiter.acquire(100, "trace2");
        long elapsed1 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed1 >= 50 && elapsed1 < 200,
                "Second request waits for previous, elapsed: " + elapsed1 + "ms");

        // acquire(100)：等待约 100ms（上一个请求预支的时间）
        start = System.nanoTime();
        limiter.acquire(100, "trace3");
        long elapsed2 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsed2 >= 50 && elapsed2 < 200,
                "Third request waits for previous, elapsed: " + elapsed2 + "ms");
    }

    @Test
    @DisplayName("tryAcquire - 空闲后立即成功")
    void testTryAcquireAfterIdle() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        // 空闲一段时间
        Thread.sleep(200);

        boolean result = limiter.tryAcquire(100, 0, TimeUnit.MILLISECONDS, "trace1");
        assertTrue(result, "tryAcquire after idle should succeed immediately");
    }

    @Test
    @DisplayName("tryAcquire - 连续请求超时不足")
    void testTryAcquireConsecutiveTimeoutFail() throws InterruptedException {
        TrafficRateLimiter limiter = new TrafficRateLimiter(1000);

        // acquire(500)：推进 nextFreeTicketMicros 500ms
        limiter.acquire(500, "trace1");

        // tryAcquire 超时 100ms，但需要等约 500ms
        boolean result = limiter.tryAcquire(100, 100, TimeUnit.MILLISECONDS, "trace2");
        assertFalse(result, "tryAcquire with insufficient timeout should fail");
    }
}
