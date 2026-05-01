package com.cq.proxy.service.batch;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingSchedulerTest
{
    private final RoutingScheduler routingScheduler = new RoutingScheduler();

    @Test
    void oneToOneBroadcastShouldDegradeToSingleRouting()
    {
        Map<String, List<String>> result = routingScheduler.resolveTargets(
                List.of("a.txt", "b.txt"),
                List.of("agent-a", "agent-b"),
                "ONE_TO_ONE",
                "BROADCAST",
                null
        );

        assertEquals(2, result.size());
        assertTrue(result.values().stream().allMatch(targets -> targets.size() == 1));
    }

    @Test
    void oneToManyBroadcastShouldReturnAllTargets()
    {
        Map<String, List<String>> result = routingScheduler.resolveTargets(
                List.of("a.txt"),
                List.of("agent-a", "agent-b", "agent-c"),
                "ONE_TO_MANY",
                "BROADCAST",
                null
        );

        assertEquals(List.of("agent-a", "agent-b", "agent-c"), result.get("a.txt"));
    }

    @Test
    void regionBasedRoutingShouldPreferConfiguredRegionAgents()
    {
        String routingConfig = """
                {
                  "defaultRegion": "default",
                  "regionAgents": {
                    "east": ["agent-east"],
                    "default": ["agent-default"]
                  },
                  "rules": [
                    {"pattern": "logs/**/*.log", "region": "east"}
                  ]
                }
                """;

        Map<String, List<String>> result = routingScheduler.resolveTargets(
                List.of("logs/app/app.log", "docs/readme.txt"),
                List.of("agent-east", "agent-default"),
                "ONE_TO_ONE",
                "REGION_BASED",
                routingConfig
        );

        assertEquals(List.of("agent-east"), result.get("logs/app/app.log"));
        assertEquals(List.of("agent-default"), result.get("docs/readme.txt"));
    }
}
