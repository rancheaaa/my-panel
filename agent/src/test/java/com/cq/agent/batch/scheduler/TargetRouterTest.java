package com.cq.agent.batch.scheduler;

import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.TransferConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TargetRouterTest {

    private TargetRouter router;
    private List<TargetAgentInfo> targets;

    @BeforeEach
    void setUp() {
        router = new TargetRouter();
        targets = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            TargetAgentInfo agent = new TargetAgentInfo();
            agent.setAgentId("agent-00" + i);
            agent.setAgentName("root@192.168.1." + i + ":7777");
            agent.setTargetDir("/data/target" + i);
            targets.add(agent);
        }
    }

    @Nested
    @DisplayName("BROADCAST策略")
    class BroadcastTest {

        @Test
        @DisplayName("广播模式返回所有目标Agent")
        void broadcastReturnsAllTargets() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("BROADCAST");

            List<TargetAgentInfo> result = router.route(targets, config);

            assertEquals(3, result.size());
            assertTrue(result.containsAll(targets));
        }

        @Test
        @DisplayName("默认策略为BROADCAST")
        void defaultStrategyIsBroadcast() {
            List<TargetAgentInfo> result = router.route(targets, null);
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("transferConfig为null时默认BROADCAST")
        void nullConfigDefaultsToBroadcast() {
            List<TargetAgentInfo> result = router.route(targets, null);
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("routingStrategy为null时默认BROADCAST")
        void nullRoutingStrategyDefaultsToBroadcast() {
            TransferConfig config = new TransferConfig();
            List<TargetAgentInfo> result = router.route(targets, config);
            assertEquals(3, result.size());
        }
    }

    @Nested
    @DisplayName("ROUND_ROBIN策略")
    class RoundRobinTest {

        @Test
        @DisplayName("轮询模式每次只选一个目标")
        void roundRobinReturnsOneTarget() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("ROUND_ROBIN");

            List<TargetAgentInfo> result = router.route(targets, config);
            assertEquals(1, result.size());
            assertTrue(targets.contains(result.getFirst()));
        }

        @Test
        @DisplayName("轮询模式依次选择不同目标")
        void roundRobinCyclesThroughTargets() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("ROUND_ROBIN");

            List<String> selectedIds = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                List<TargetAgentInfo> result = router.route(targets, config);
                selectedIds.add(result.getFirst().getAgentId());
            }

            long uniqueCount = selectedIds.stream().distinct().count();
            assertTrue(uniqueCount >= 2, "轮询应选择至少2个不同目标");
        }
    }

    @Nested
    @DisplayName("RANDOM策略")
    class RandomTest {

        @Test
        @DisplayName("随机模式每次只选一个目标")
        void randomReturnsOneTarget() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("RANDOM");

            List<TargetAgentInfo> result = router.route(targets, config);
            assertEquals(1, result.size());
            assertTrue(targets.contains(result.getFirst()));
        }

        @Test
        @DisplayName("随机模式多次调用应覆盖不同目标")
        void randomCoversDifferentTargets() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("RANDOM");

            List<String> selectedIds = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                List<TargetAgentInfo> result = router.route(targets, config);
                selectedIds.add(result.getFirst().getAgentId());
            }

            long uniqueCount = selectedIds.stream().distinct().count();
            assertTrue(uniqueCount >= 2, "100次随机应至少选到2个不同目标");
        }
    }

    @Nested
    @DisplayName("REGION_BASED策略")
    class RegionBasedTest {

        @Test
        @DisplayName("区域匹配时返回对应Agent")
        void regionBasedReturnsMatchedAgents() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("REGION_BASED");
            config.setRoutingConfig("{\"regionMapping\":{\"region-a\":[\"agent-001\",\"agent-002\"],\"region-b\":[\"agent-003\"]},\"defaultRegion\":\"region-a\"}");

            List<TargetAgentInfo> result = router.route(targets, config);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("routingConfig为空时降级为BROADCAST")
        void regionBasedEmptyConfigFallsBackToBroadcast() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("REGION_BASED");
            config.setRoutingConfig("");

            List<TargetAgentInfo> result = router.route(targets, config);
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("routingConfig为null时降级为BROADCAST")
        void regionBasedNullConfigFallsBackToBroadcast() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("REGION_BASED");

            List<TargetAgentInfo> result = router.route(targets, config);
            assertEquals(3, result.size());
        }
    }

    @Nested
    @DisplayName("边界条件")
    class EdgeCaseTest {

        @Test
        @DisplayName("空目标列表返回空列表")
        void emptyTargetsReturnsEmpty() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("BROADCAST");

            List<TargetAgentInfo> result = router.route(List.of(), config);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("null目标列表返回空列表")
        void nullTargetsReturnsEmpty() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("BROADCAST");

            List<TargetAgentInfo> result = router.route(null, config);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("单个目标Agent时直接返回")
        void singleTargetReturnsDirectly() {
            TargetAgentInfo single = targets.getFirst();
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("ROUND_ROBIN");

            List<TargetAgentInfo> result = router.route(List.of(single), config);
            assertEquals(1, result.size());
            assertEquals(single.getAgentId(), result.getFirst().getAgentId());
        }

        @Test
        @DisplayName("未知策略降级为BROADCAST")
        void unknownStrategyFallsBackToBroadcast() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("UNKNOWN_STRATEGY");

            List<TargetAgentInfo> result = router.route(targets, config);
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("策略大小写不敏感")
        void strategyCaseInsensitive() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("broadcast");

            List<TargetAgentInfo> result = router.route(targets, config);
            assertEquals(3, result.size());
        }
    }
}
