package com.cq.agent.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 流量限流器，基于 Guava RateLimiter 的 SmoothBursty 算法实现。
 *
 * 核心设计思想（来自 Guava）：
 * 1. 维护 storedPermits（存储的许可）和 nextFreeTicketMicros（下一个空闲 ticket 时间）
 * 2. 当限流器空闲时，storedPermits 逐渐增加（最多到 maxPermits）
 * 3. 当请求到达时，优先消耗 storedPermits（SmoothBursty 中消耗 storedPermits 无需等待），
 *    剩余的通过 fresh permits 提供，等待时间 = permits * stableInterval
 * 4. 关键特性：请求不会被当前 acquire 阻塞（当前请求立即通过），
 *    而是将等待成本转嫁给下一个请求（预支未来时间）
 *
 * 对于 SmoothBursty：
 * - storedPermitsToWaitTime = 0（存储的许可无需额外等待，允许突发）
 * - maxBurstSeconds = 1.0（最多存储 1 秒的许可）
 */
@SuppressWarnings("all")
public class TrafficRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(TrafficRateLimiter.class);

    /**
     * 限流器是否已关闭
     */
    private volatile boolean shutdown = false;

    // ==================== SleepingStopwatch 内部实现 ====================

    private final long startNanos;

    private long readMicros() {
        return TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startNanos);
    }

    private static void sleepMicrosUninterruptibly(long micros) {
        if (micros <= 0) {
            return;
        }
        long nanos = TimeUnit.MICROSECONDS.toNanos(micros);
        long remainingNanos = nanos;
        long end = System.nanoTime() + remainingNanos;
        while (true) {
            try {
                Thread.sleep(TimeUnit.NANOSECONDS.toMillis(remainingNanos),
                        (int) (remainingNanos % 1_000_000));
                return;
            } catch (InterruptedException e) {
                remainingNanos = end - System.nanoTime();
                if (remainingNanos <= 0) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    // ==================== SmoothBursty 核心算法 ====================

    /**
     * 存储的许可数量（空闲时积累）
     */
    private double storedPermits;

    /**
     * 最大存储许可数量 = permitsPerSecond * maxBurstSeconds
     */
    private double maxPermits;

    /**
     * 两个连续许可之间的稳定间隔（微秒）= 1秒/permitsPerSecond
     */
    private double stableIntervalMicros;

    /**
     * 下一个请求可以被授予的时间（微秒，相对于启动时间）
     * 这是 Guava RateLimiter 的核心：不是记住上一个请求的时间，
     * 而是记住下一个请求的预期到达时间
     */
    private long nextFreeTicketMicros;

    /**
     * 最大突发秒数，默认1.0秒（与 Guava 一致）
     */
    private static final double MAX_BURST_SECONDS = 1.0;

    /**
     * 用于同步的 mutex 对象
     */
    private final Object mutex = new Object();

    /**
     * 创建一个流量限流器
     *
     * @param bytesPerSecond 每秒允许传输的字节数，必须大于0
     */
    public TrafficRateLimiter(long bytesPerSecond) {
        if (bytesPerSecond <= 0) {
            throw new IllegalArgumentException("bytesPerSecond must be positive: " + bytesPerSecond);
        }
        this.startNanos = System.nanoTime();
        this.storedPermits = 0.0;
        this.maxPermits = 0.0;
        this.nextFreeTicketMicros = 0L;
        // 初始化速率
        doSetRate(bytesPerSecond, readMicros());
    }

    /**
     * 获取指定字节数的传输许可，阻塞直到请求可以被授予。
     *
     * 这是 Guava RateLimiter.acquire() 的等价实现：
     * 1. 计算需要等待的微秒数（reserve）
     * 2. 休眠等待
     * 3. 返回等待的秒数
     *
     * @param bytes   需要传输的字节数
     * @param traceId 追踪ID（用于日志）
     * @throws InterruptedException 如果线程被中断
     */
    public void acquire(int bytes, String traceId) throws InterruptedException {
        if (shutdown) {
            return;
        }
        if (bytes <= 0) {
            return;
        }
        long microsToWait = reserve(bytes);
        if (microsToWait > 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("[traceId={}] 限流等待: {}ms (bytes={})",
                        traceId, TimeUnit.MICROSECONDS.toMillis(microsToWait), bytes);
            }
            // 使用可中断的 sleep，支持 InterruptedException
            long millis = TimeUnit.MICROSECONDS.toMillis(microsToWait);
            long extraNanos = TimeUnit.MICROSECONDS.toNanos(microsToWait) % 1_000_000;
            Thread.sleep(millis, (int) extraNanos);
        }
    }

    /**
     * 尝试获取指定字节数的传输许可，在指定超时时间内。
     * 如果在超时时间内无法获取许可，则返回 false。
     *
     * @param bytes       需要传输的字节数
     * @param timeout     最大等待时间
     * @param unit        时间单位
     * @param traceId     追踪ID
     * @return 如果获取到许可返回 true，否则返回 false
     * @throws InterruptedException 如果线程被中断
     */
    public boolean tryAcquire(int bytes, long timeout, TimeUnit unit, String traceId) throws InterruptedException {
        if (shutdown) {
            return true;
        }
        if (bytes <= 0) {
            return true;
        }
        long timeoutMicros = Math.max(unit.toMicros(timeout), 0);
        long microsToWait;
        synchronized (mutex) {
            long nowMicros = readMicros();
            if (nextFreeTicketMicros > nowMicros + timeoutMicros) {
                return false;
            }
            microsToWait = reserveAndGetWaitLength(bytes, nowMicros);
        }
        if (microsToWait > 0) {
            long millis = TimeUnit.MICROSECONDS.toMillis(microsToWait);
            long extraNanos = TimeUnit.MICROSECONDS.toNanos(microsToWait) % 1_000_000;
            Thread.sleep(millis, (int) extraNanos);
        }
        return true;
    }

    /**
     * 关闭限流器
     */
    public void shutdown() {
        this.shutdown = true;
    }

    /**
     * 获取当前配置的速率（字节/秒）
     */
    public double getRate() {
        synchronized (mutex) {
            return SECONDS.toMicros(1L) / stableIntervalMicros;
        }
    }

    /**
     * 动态更新速率
     */
    public void setRate(long bytesPerSecond) {
        if (bytesPerSecond <= 0) {
            throw new IllegalArgumentException("bytesPerSecond must be positive: " + bytesPerSecond);
        }
        synchronized (mutex) {
            doSetRate(bytesPerSecond, readMicros());
        }
    }

    // ==================== 内部方法（Guava SmoothRateLimiter + SmoothBursty 算法） ====================

    /**
     * 预留指定数量的许可，返回需要等待的微秒数
     */
    private long reserve(int bytes) {
        checkPermits(bytes);
        synchronized (mutex) {
            return reserveAndGetWaitLength(bytes, readMicros());
        }
    }

    /**
     * 核心算法：预留许可并计算等待时间
     *
     * 这就是 Guava RateLimiter 的核心逻辑：
     * 1. 先积累 storedPermits（从上次到现在经过的时间 × 速率）
     * 2. 计算本次请求需要等待的时间
     * 3. 将等待成本记到 nextFreeTicketMicros（预支未来）
     */
    private long reserveAndGetWaitLength(int permits, long nowMicros) {
        long momentAvailable = reserveEarliestAvailable(permits, nowMicros);
        return Math.max(momentAvailable - nowMicros, 0);
    }

    /**
     * 预留最早的可用时间
     *
     * Guava 算法核心：
     * - storedPermits 优先被消耗（SmoothBursty 中消耗 storedPermits 无需等待）
     * - 剩余的通过 fresh permits 提供，等待时间 = permits * stableInterval
     * - 等待时间不是加在当前请求上，而是加在 nextFreeTicketMicros 上（预支未来）
     * - 这意味着当前请求只需等待 nextFreeTicketMicros - nowMicros
     */
    private long reserveEarliestAvailable(int requiredPermits, long nowMicros) {
        resync(nowMicros);
        long returnValue = nextFreeTicketMicros;

        double storedPermitsToSpend = Math.min(requiredPermits, this.storedPermits);
        double freshPermits = requiredPermits - storedPermitsToSpend;

        // SmoothBursty: storedPermitsToWaitTime = 0（存储许可无需等待）
        long waitMicros = storedPermitsToWaitTime(this.storedPermits, storedPermitsToSpend)
                + (long) (freshPermits * stableIntervalMicros);

        this.nextFreeTicketMicros = saturatedAdd(nextFreeTicketMicros, waitMicros);
        this.storedPermits -= storedPermitsToSpend;

        return returnValue;
    }

    /**
     * 同步 storedPermits：将空闲时间转化为存储的许可
     *
     * 如果 nextFreeTicketMicros 在过去（说明限流器有一段时间没被使用），
     * 则将这段时间转化为 storedPermits
     */
    private void resync(long nowMicros) {
        if (nowMicros > nextFreeTicketMicros) {
            double newPermits = (nowMicros - nextFreeTicketMicros) / coolDownIntervalMicros();
            storedPermits = Math.min(maxPermits, storedPermits + newPermits);
            nextFreeTicketMicros = nowMicros;
        }
    }

    /**
     * SmoothBursty: 存储许可转化为等待时间 = 0
     * 这意味着存储的许可可以立即被使用，允许突发流量
     */
    private long storedPermitsToWaitTime(double storedPermits, double permitsToTake) {
        return 0L;
    }

    /**
     * 冷却间隔：从0到maxPermits需要 maxBurstSeconds 秒
     * coolDownIntervalMicros = maxBurstSeconds * 1_000_000 / maxPermits
     * 但由于 maxPermits = permitsPerSecond * maxBurstSeconds，
     * 所以 coolDownIntervalMicros = 1_000_000 / permitsPerSecond = stableIntervalMicros
     */
    private double coolDownIntervalMicros() {
        return stableIntervalMicros;
    }

    /**
     * 设置速率（Guava SmoothBursty.doSetRate）
     */
    private void doSetRate(double permitsPerSecond, long nowMicros) {
        resync(nowMicros);
        double stableIntervalMicros = SECONDS.toMicros(1L) / permitsPerSecond;
        this.stableIntervalMicros = stableIntervalMicros;

        double oldMaxPermits = this.maxPermits;
        // SmoothBursty: maxPermits = maxBurstSeconds * permitsPerSecond
        this.maxPermits = MAX_BURST_SECONDS * permitsPerSecond;

        if (oldMaxPermits == Double.POSITIVE_INFINITY) {
            storedPermits = 0.0;
        } else if (oldMaxPermits == 0.0) {
            // 初始状态：storedPermits = 0（没有积累的许可）
            storedPermits = 0.0;
        } else {
            storedPermits = storedPermits * maxPermits / oldMaxPermits;
        }
    }

    private static void checkPermits(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("Requested permits must be positive");
        }
    }

    /**
     * 饱和加法：防止 long 溢出
     */
    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        if ((a ^ b) < 0 | (a ^ sum) >= 0) {
            return sum;
        }
        return Long.MAX_VALUE;
    }

    private static final TimeUnit SECONDS = TimeUnit.SECONDS;
}
