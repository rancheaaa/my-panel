package com.cq.proxy.service.batch.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BroadcastRoutingStrategy implements RoutingStrategy
{
    @Override
    public String selectTarget(String filePath, List<String> candidates, Map<String, Object> context)
    {
        throw new UnsupportedOperationException("Broadcast strategy should be handled separately, not via selectTarget");
    }

    public List<String> selectAllTargets(List<String> candidates)
    {
        return new ArrayList<>(candidates);
    }
}
