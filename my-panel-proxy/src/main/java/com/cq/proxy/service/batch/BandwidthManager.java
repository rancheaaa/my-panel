package com.cq.proxy.service.batch;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class BandwidthManager
{
    private final AtomicLong globalBandwidthBytesPerSec = new AtomicLong(0);
    private final ConcurrentHashMap<Long, Long> taskBandwidthMap = new ConcurrentHashMap<>();

    public void allocateTaskBandwidth(Long taskId, Long maxBandwidthKbS)
    {
        if (maxBandwidthKbS != null)
        {
            taskBandwidthMap.put(taskId, maxBandwidthKbS * 1024L);
        }
    }

    public void removeTaskBandwidth(Long taskId)
    {
        taskBandwidthMap.remove(taskId);
    }

    public long getAvailableBandwidth(Long taskId)
    {
        Long taskLimit = taskBandwidthMap.get(taskId);
        if (taskLimit != null && taskLimit > 0) return taskLimit;
        long global = globalBandwidthBytesPerSec.get();
        return global > 0 ? global : Long.MAX_VALUE;
    }

    public void setGlobalBandwidth(long bytesPerSec)
    {
        globalBandwidthBytesPerSec.set(bytesPerSec);
    }
}
