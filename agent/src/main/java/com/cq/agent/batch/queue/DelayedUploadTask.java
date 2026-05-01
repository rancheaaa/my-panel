package com.cq.agent.batch.queue;

import java.util.Date;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class DelayedUploadTask implements Delayed
{
    private final BatchUploadTask task;
    private final long executeTimeMs;

    public DelayedUploadTask(BatchUploadTask task, long delayMs)
    {
        this.task = task;
        this.executeTimeMs = System.currentTimeMillis() + delayMs;
    }

    public BatchUploadTask getTask()
    {
        return task;
    }

    public Date getExecuteAt()
    {
        return new Date(executeTimeMs);
    }

    @Override
    public long getDelay(TimeUnit unit)
    {
        long diff = executeTimeMs - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o)
    {
        return Long.compare(this.executeTimeMs, ((DelayedUploadTask) o).executeTimeMs);
    }
}
