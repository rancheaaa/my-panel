package com.cq.proxy.service.batch.strategy;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

public class RandomRoutingStrategy implements RoutingStrategy
{
    private final SecureRandom random = new SecureRandom();

    @Override
    public String selectTarget(String filePath, List<String> candidates, Map<String, Object> context)
    {
        if (candidates == null || candidates.isEmpty()) return null;
        int index = random.nextInt(candidates.size());
        return candidates.get(index);
    }
}
