package com.cq.proxy.service.batch;

import com.cq.proxy.service.batch.strategy.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoutingScheduler
{
    private final Map<String, RoutingStrategy> strategyMap = new ConcurrentHashMap<>();

    public RoutingScheduler()
    {
        strategyMap.put("SINGLE", new SingleRoutingStrategy());
        strategyMap.put("ROUND_ROBIN", new RoundRobinRoutingStrategy());
        strategyMap.put("BROADCAST", new BroadcastRoutingStrategy());
        strategyMap.put("RANDOM", new RandomRoutingStrategy());
    }

    public Map<String, List<String>> resolveTargets(
            List<String> filePaths, List<String> targetAgents,
            String transferMode, String routingStrategy, String routingConfig)
    {
        if (filePaths == null || filePaths.isEmpty() || targetAgents == null || targetAgents.isEmpty())
        {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        String effectiveStrategy = routingStrategy == null || routingStrategy.isBlank() ? "BROADCAST" : routingStrategy;
        if ("ONE_TO_ONE".equals(transferMode) && "BROADCAST".equals(routingStrategy))
        {
            effectiveStrategy = "SINGLE";
        }
        if ("BROADCAST".equals(effectiveStrategy))
        {
            BroadcastRoutingStrategy broadcastStrategy = (BroadcastRoutingStrategy) strategyMap.get("BROADCAST");
            List<String> allTargets = broadcastStrategy.selectAllTargets(targetAgents);
            for (String filePath : filePaths)
            {
                result.put(filePath, allTargets);
            }
            return result;
        }
        RoutingStrategy strategy = strategyMap.get(effectiveStrategy);
        if ("REGION_BASED".equals(effectiveStrategy))
        {
            strategy = new RegionBasedRoutingStrategy(routingConfig);
        }
        else if (strategy == null)
        {
            strategy = strategyMap.get("SINGLE");
        }
        Map<String, Object> context = new HashMap<>();
        context.put("routingConfig", routingConfig);
        for (String filePath : filePaths)
        {
            String target = strategy.selectTarget(filePath, targetAgents, context);
            if (target != null)
            {
                result.put(filePath, List.of(target));
            }
        }
        return result;
    }
}
