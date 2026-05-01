package com.cq.proxy.service.batch.strategy;

import java.util.List;
import java.util.Map;

public interface RoutingStrategy
{
    String selectTarget(String filePath, List<String> candidates, Map<String, Object> context);
}
