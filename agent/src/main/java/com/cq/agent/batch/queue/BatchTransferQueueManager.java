package com.cq.agent.batch.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class BatchTransferQueueManager
{
    private static final Logger logger = LoggerFactory.getLogger(BatchTransferQueueManager.class);
    private final PriorityBlockingQueue<BatchUploadTask> sendQueue;
    private final DelayQueue<DelayedUploadTask> retryQueue;
    private final int sendQueueCapacity;
    private final int retryQueueCapacity;
    private final int maxRetries;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final long throughputWindowMs;
    private final AtomicInteger sendQueuePeakDepth = new AtomicInteger();
    private final AtomicInteger retryQueuePeakDepth = new AtomicInteger();
    private final AtomicInteger sendQueueSize = new AtomicInteger();
    private final AtomicInteger retryQueueSize = new AtomicInteger();
    private final LongAdder totalWaitTimeMs = new LongAdder();
    private final LongAdder totalDequeued = new LongAdder();
    private final LongAdder completedCount = new LongAdder();
    private final LongAdder failedCount = new LongAdder();
    private final Deque<Long> processedTimestamps = new ArrayDeque<>();
    private final ScheduledExecutorService retryScheduler;

    public BatchTransferQueueManager(int capacity)
    {
        this(capacity, 5000, 3, 1000, 60000, 60000);
    }

    public BatchTransferQueueManager(int sendQueueCapacity,
                                     int retryQueueCapacity,
                                     int maxRetries,
                                     long baseDelayMs,
                                     long maxDelayMs,
                                     long throughputWindowMs)
    {
        this.sendQueueCapacity = sendQueueCapacity;
        this.retryQueueCapacity = retryQueueCapacity;
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.throughputWindowMs = throughputWindowMs;
        this.sendQueue = new PriorityBlockingQueue<>(Math.max(1, sendQueueCapacity),
                Comparator.comparingInt(BatchUploadTask::getPriority).reversed()
                        .thenComparingLong(BatchUploadTask::getEnqueueTime));
        this.retryQueue = new DelayQueue<>();
        this.retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "batch-retry-scheduler");
            t.setDaemon(true);
            return t;
        });
        startRetryScheduler();
    }

    public boolean enqueue(BatchUploadTask task)
    {
        if (task == null)
        {
            return false;
        }
        if (sendQueueSize.incrementAndGet() > sendQueueCapacity)
        {
            sendQueueSize.decrementAndGet();
            return false;
        }
        task.setEnqueueTime(System.currentTimeMillis());
        boolean offered = sendQueue.offer(task);
        if (offered)
        {
            int depth = sendQueue.size();
            updatePeak(sendQueuePeakDepth, depth);
        }
        else
        {
            sendQueueSize.decrementAndGet();
        }
        return offered;
    }

    public BatchUploadTask dequeue(long timeoutMs) throws InterruptedException
    {
        BatchUploadTask task = sendQueue.poll(Math.max(0L, timeoutMs), TimeUnit.MILLISECONDS);
        if (task != null)
        {
            sendQueueSize.decrementAndGet();
            long waitTime = System.currentTimeMillis() - task.getEnqueueTime();
            totalWaitTimeMs.add(Math.max(0L, waitTime));
            totalDequeued.increment();
        }
        return task;
    }

    public BatchUploadTask peek()
    {
        return sendQueue.peek();
    }

    public void enqueueForRetry(BatchUploadTask task, long delayMs)
    {
        enqueueForRetryAndGetScheduleTime(task, delayMs);
    }

    public Date enqueueForRetryAndGetScheduleTime(BatchUploadTask task, long delayMs)
    {
        if (task == null)
        {
            return null;
        }
        if (task.getRetryCount() >= maxRetries)
        {
            logger.warn("Retry limit reached for subtask {}, maxRetries={}", task.getSubtaskId(), maxRetries);
            recordFailure();
            return null;
        }
        if (retryQueueSize.incrementAndGet() > retryQueueCapacity)
        {
            retryQueueSize.decrementAndGet();
            logger.error("Retry queue is full, dropping subtask {}", task.getSubtaskId());
            recordFailure();
            return null;
        }
        task.setRetryCount(task.getRetryCount() + 1);
        long effectiveDelay = normalizeDelay(delayMs, task.getRetryCount());
        DelayedUploadTask delayedUploadTask = new DelayedUploadTask(task, effectiveDelay);
        retryQueue.offer(delayedUploadTask);
        int depth = retryQueue.size();
        updatePeak(retryQueuePeakDepth, depth);
        return delayedUploadTask.getExecuteAt();
    }

    public void recordCompletion()
    {
        completedCount.increment();
        recordProcessedTimestamp();
    }

    public void recordFailure()
    {
        failedCount.increment();
        recordProcessedTimestamp();
    }

    public QueueMetrics getMetrics()
    {
        evictExpiredProcessedEntries(System.currentTimeMillis());
        QueueMetrics m = new QueueMetrics();
        m.setSendQueueDepth(sendQueue.size());
        m.setSendQueuePeakDepth(sendQueuePeakDepth.get());
        m.setSendQueueCapacity(sendQueueCapacity);
        m.setSendQueueUtilizationPct(sendQueueCapacity > 0 ? (sendQueue.size() * 100.0 / sendQueueCapacity) : 0.0);
        long dequeued = totalDequeued.sum();
        m.setSendQueueAvgWaitMs(dequeued > 0 ? totalWaitTimeMs.sum() / dequeued : 0);
        m.setRetryQueueDepth(retryQueue.size());
        m.setRetryQueuePeakDepth(retryQueuePeakDepth.get());
        m.setRetryQueueCapacity(retryQueueCapacity);
        DelayedUploadTask nextTask = retryQueue.peek();
        m.setRetryNextScheduleTime(nextTask == null ? null : nextTask.getExecuteAt());
        long total = completedCount.sum() + failedCount.sum();
        m.setSuccessRatePct(total > 0 ? (completedCount.sum() * 100.0 / total) : 100.0);
        m.setProcessingRatePerSec(processedTimestamps.isEmpty() ? 0D : (processedTimestamps.size() * 1000D / throughputWindowMs));
        return m;
    }

    public int getSendQueueDepth()
    {
        return sendQueue.size();
    }

    public int getRetryQueueDepth()
    {
        return retryQueue.size();
    }

    public void close()
    {
        retryScheduler.shutdownNow();
    }

    private void startRetryScheduler()
    {
        retryScheduler.scheduleAtFixedRate(() -> {
            try
            {
                while (true)
                {
                    DelayedUploadTask delayed = retryQueue.poll();
                    if (delayed == null)
                    {
                        break;
                    }
                    retryQueueSize.decrementAndGet();
                    BatchUploadTask task = delayed.getTask();
                    if (!enqueue(task))
                    {
                        logger.warn("Send queue full, re-queueing retry task for subtask {}", task.getSubtaskId());
                        enqueueForRetry(task, Math.min(maxDelayMs, 5000L));
                        break;
                    }
                }
            }
            catch (Exception e)
            {
                logger.error("Retry scheduler error: {}", e.getMessage());
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private long normalizeDelay(long requestedDelayMs, int retryCount)
    {
        if (requestedDelayMs > 0)
        {
            return Math.min(requestedDelayMs, maxDelayMs);
        }
        long multiplier = 1L << Math.max(0, retryCount - 1);
        return Math.min(baseDelayMs * multiplier, maxDelayMs);
    }

    private void updatePeak(AtomicInteger peakHolder, int candidate)
    {
        peakHolder.accumulateAndGet(candidate, Math::max);
    }

    private synchronized void recordProcessedTimestamp()
    {
        long now = System.currentTimeMillis();
        processedTimestamps.addLast(now);
        evictExpiredProcessedEntries(now);
    }

    private synchronized void evictExpiredProcessedEntries(long now)
    {
        while (!processedTimestamps.isEmpty() && now - processedTimestamps.peekFirst() > throughputWindowMs)
        {
            processedTimestamps.removeFirst();
        }
    }
}
