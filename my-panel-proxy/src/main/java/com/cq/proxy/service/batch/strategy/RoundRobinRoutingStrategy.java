package com.cq.proxy.service.batch.strategy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinRoutingStrategy implements RoutingStrategy
{
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public String selectTarget(String filePath, List<String> candidates, Map<String, Object> context)
    {
        if (candidates == null || candidates.isEmpty()) return null;
        int index = Math.abs(counter.getAndIncrement()) % candidates.size();
        return candidates.get(index);
    }
}
