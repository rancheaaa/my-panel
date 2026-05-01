package com.cq.proxy.service.batch.strategy;

import java.util.List;
import java.util.Map;

public class SingleRoutingStrategy implements RoutingStrategy
{
    @Override
    public String selectTarget(String filePath, List<String> candidates, Map<String, Object> context)
    {
        if (candidates == null || candidates.isEmpty()) return null;
        int index = Math.abs(filePath.hashCode()) % candidates.size();
        return candidates.get(index);
    }
}
