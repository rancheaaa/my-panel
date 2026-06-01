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
        for (int i = 1; i <= 5; i++) {
            TargetAgentInfo agent = new TargetAgentInfo();
            agent.setAgentId("agent-00" + i);
            agent.setAgentName("root@192.168.1." + i + ":7777");
            agent.setTargetDir("/data/target" + i);
            targets.add(agent);
        }
    }

    private TransferConfig createRegionConfig(String json) {
        TransferConfig config = new TransferConfig();
        config.setRoutingStrategy("REGION_BASED");
        config.setRoutingConfig(json);
        return config;
    }

    private String buildRegionJson(String sourceRegion, String defaultRegion, String fallbackStrategy,
                                    String... regionEntries) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"regionMapping\":{");
        for (int i = 0; i < regionEntries.length; i += 2) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(regionEntries[i]).append("\":").append(regionEntries[i + 1]);
        }
        sb.append("}");
        if (sourceRegion != null) {
            sb.append(",\"sourceRegion\":\"").append(sourceRegion).append("\"");
        }
        if (defaultRegion != null) {
            sb.append(",\"defaultRegion\":\"").append(defaultRegion).append("\"");
        }
        if (fallbackStrategy != null) {
            sb.append(",\"fallbackStrategy\":\"").append(fallbackStrategy).append("\"");
        }
        sb.append("}");
        return sb.toString();
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

            assertEquals(5, result.size());
            assertTrue(result.containsAll(targets));
        }

        @Test
        @DisplayName("默认策略为BROADCAST")
        void defaultStrategyIsBroadcast() {
            List<TargetAgentInfo> result = router.route(targets, null);
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("transferConfig为null时默认BROADCAST")
        void nullConfigDefaultsToBroadcast() {
            List<TargetAgentInfo> result = router.route(targets, null);
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("routingStrategy为null时默认BROADCAST")
        void nullRoutingStrategyDefaultsToBroadcast() {
            TransferConfig config = new TransferConfig();
            List<TargetAgentInfo> result = router.route(targets, config);
            assertEquals(5, result.size());
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
            for (int i = 0; i < 10; i++) {
                List<TargetAgentInfo> result = router.route(targets, config);
                selectedIds.add(result.getFirst().getAgentId());
            }

            long uniqueCount = selectedIds.stream().distinct().count();
            assertTrue(uniqueCount >= 2, "轮询应选择至少2个不同目标，实际: " + uniqueCount);
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
            assertTrue(uniqueCount >= 2, "100次随机应至少选到2个不同目标，实际: " + uniqueCount);
        }
    }

    @Nested
    @DisplayName("REGION_BASED策略 - 正常匹配")
    class RegionBasedNormalMatch {

        @Test
        @DisplayName("sourceRegion指定后精确匹配区域内的所有Agent")
        void sourceRegionMatchesExactAgents() {
            String json = buildRegionJson(
                    "region-a", null, null,
                    "region-a", "[\"agent-001\",\"agent-002\"]",
                    "region-b", "[\"agent-003\",\"agent-004\",\"agent-005\"]"
            );

            List<TargetAgentInfo> result = router.route(targets, createRegionConfig(json));

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(a -> a.getAgentId().equals("agent-001") || a.getAgentId().equals("agent-002")));
        }

        @Test
        @DisplayName("sourceRegion指向只含单个Agent的区域返回1个结果")
        void singleAgentInRegionReturnsOneResult() {
            String json = buildRegionJson(
                    "region-b", null, null,
                    "region-a", "[\"agent-001\",\"agent-002\"]",
                    "region-b", "[\"agent-003\"]"
            );

            List<TargetAgentInfo> result = router.route(targets, createRegionConfig(json));

            assertEquals(1, result.size());
            assertEquals("agent-003", result.getFirst().getAgentId());
        }

        @Test
        @DisplayName("sourceRegion未设置时使用defaultRegion")
        void fallsBackToDefaultRegionWhenNoSourceRegion() {
            String json = buildRegionJson(
                    null, "region-b", null,
                    "region-a", "[\"agent-001\",\"agent-002\"]",
                    "region-b", "[\"agent-003\",\"agent-004\"]"
            );

            List<TargetAgentInfo> result = router.route(targets, createRegionConfig(json));

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(a -> a.getAgentId().equals("agent-003") || a.getAgentId().equals("agent-004")));
        }

        @Test
        @DisplayName("sourceRegion和defaultRegion都未设置时降级为BROADCAST")
        void noRegionConfiguredFallsBackToBroadcast() {
            String json = buildRegionJson(
                    null, null, null,
                    "region-a", "[\"agent-001\",\"agent-002\"]"
            );

            List<TargetAgentInfo> result = router.route(targets, createRegionConfig(json));
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("sourceRegion指定的区域在targets中部分匹配（部分Agent不在列表中）")
        void partialMatchOnlyReturnsExistingTargets() {
            String json = buildRegionJson(
                    "region-x", null, null,
                    "region-x", "[\"agent-001\",\"agent-999\",\"agent-888\"]"
            );

            List<TargetAgentInfo> result = router.route(targets, createRegionConfig(json));

            assertEquals(1, result.size());
            assertEquals("agent-001", result.getFirst().getAgentId());
        }

        @Test
        @DisplayName("sourceRegion包含全部5个Agent时返回全部")
        void allAgentsInSameRegion() {
            String json = buildRegionJson(
                    "all-region", null, null,
                    "all-region", "[\"agent-001\",\"agent-002\",\"agent-003\",\"agent-004\",\"agent-005\"]"
            );

            List<TargetAgentInfo> result = router.route(targets, createRegionConfig(json));

            assertEquals(5, result.size());
        }
    }

    @Nested
    @DisplayName("REGION_BASED策略 - 异常和边界场景")
    class RegionBasedEdgeCases {

        @Test
        @DisplayName("routingConfig为空字符串时降级为BROADCAST")
        void emptyConfigFallsBackToBroadcast() {
            TransferConfig config = createRegionConfig("");

            List<TargetAgentInfo> result = router.route(targets, config);
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("routingConfig为null时降级为BROADCAST")
        void nullConfigFallsBackToBroadcast() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("REGION_BASED");

            List<TargetAgentInfo> result = router.route(targets, config);
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("routingConfig为无效JSON时降级为BROADCAST")
        void invalidJsonFallsBackToBroadcast() {
            TransferConfig config = createRegionConfig("this is not json {{{");

            List<TargetAgentInfo> result = router.route(targets, config);
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("regionMapping为空对象时降级为BROADCAST")
        void emptyRegionMappingFallsBackToBroadcast() {
            String json = "{\"sourceRegion\":\"region-a\",\"defaultRegion\":\"region-a\",\"regionMapping\":{}}";

            List<TargetAgentInfo> result = router.route(targets, createRegionConfig(json));
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("sourceRegion指向不存在的区域名时降级为BROADCAST")
        void nonExistentSourceRegionFallsBackToBroadcast() {
            String json = buildRegionJson(
                    "non-existent", null, null,
                    "region-a", "[\"agent-001\"]"
            );

            List<TargetAgentInfo> result = router.route(targets, createRegionConfig(json));
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("区域内的AgentId列表为空时降级为BROADCAST")
        void emptyAgentListInRegionFallsBackToBroadcast() {
            String json = buildRegionJson(
                    "empty-region", null, null,
                    "empty-region", "[]",
                    "region-a", "[\"agent-001\"]"
            );

            List<TargetAgentInfo> result = router.route(targets, createRegionConfig(json));
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("区域配置中无regionMapping字段时降级为BROADCAST")
        void missingRegionMappingFallsBackToBroadcast() {
            String json = "{\"sourceRegion\":\"region-a\",\"defaultRegion\":\"region-a\"}";

            List<TargetAgentInfo> result = router.route(targets, createRegionConfig(json));
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("JSON只有花括号时降级为BROADCAST")
        void emptyJsonObjectFallsBackToBroadcast() {
            List<TargetAgentInfo> result = router.route(targets, createRegionConfig("{}"));
            assertEquals(5, result.size());
        }
    }

    @Nested
    @DisplayName("REGION_BASED策略 - fallbackStrategy机制")
    class RegionBasedFallbackStrategy {

        @Test
        @DisplayName("fallbackStrategy=ROUND_ROBIN时未匹配到Agent使用轮询")
        void fallbackRoundRobinWhenNoMatch() {
            String json = "{\"regionMapping\":{\"ghost\":[\"agent-999\"]},\"sourceRegion\":\"ghost\",\"fallbackStrategy\":\"ROUND_ROBIN\"}";

            List<TargetAgentInfo> result = router.route(targets, createRegionConfig(json));

            assertEquals(1, result.size());
            assertTrue(targets.contains(result.getFirst()));
        }

        @Test
        @DisplayName("fallbackStrategy=RANDOM时未匹配到Agent使用随机")
        void fallbackRandomWhenNoMatch() {
            String json = "{\"regionMapping\":{\"ghost\":[\"agent-999\"]},\"sourceRegion\":\"ghost\",\"fallbackStrategy\":\"RANDOM\"}";

            List<TargetAgentInfo> result = router.route(targets, createRegionConfig(json));

            assertEquals(1, result.size());
            assertTrue(targets.contains(result.getFirst()));
        }

        @Test
        @DisplayName("fallbackStrategy不识别时默认降级为BROADCAST")
        void unknownFallbackStrategyDefaultsToBroadcast() {
            String json = "{\"regionMapping\":{\"ghost\":[\"agent-999\"]},\"sourceRegion\":\"ghost\",\"fallbackStrategy\":\"UNKNOWN_STRATEGY\"}";

            List<TargetAgentInfo> result = router.route(targets, createRegionConfig(json));

            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("fallbackStrategy=BROADCAST且未匹配时返回全部")
        void explicitBroadcastFallbackReturnsAll() {
            String json = "{\"regionMapping\":{\"ghost\":[\"agent-999\"]},\"sourceRegion\":\"ghost\",\"fallbackStrategy\":\"BROADCAST\"}";

            List<TargetAgentInfo> result = router.route(targets, createRegionConfig(json));

            assertEquals(5, result.size());
        }
    }

    @Nested
    @DisplayName("RegionRoutingConfig解析测试")
    class RegionRoutingConfigParsingTest {

        @Test
        @DisplayName("完整JSON正确解析所有字段")
        void parseFullConfig() {
            String json = """
                {
                  "regionMapping": {
                    "region-a": ["agent-001","agent-002"],
                    "region-b": ["agent-003"]
                  },
                  "sourceRegion": "region-a",
                  "defaultRegion": "region-b",
                  "fallbackStrategy": "RANDOM"
                }
                """;

            RegionRoutingConfig config = RegionRoutingConfig.fromJson(json);

            assertNotNull(config);
            assertEquals("region-a", config.getSourceRegion());
            assertEquals("region-b", config.getDefaultRegion());
            assertEquals("RANDOM", config.getFallbackStrategy());
            assertTrue(config.hasRegion("region-a"));
            assertTrue(config.hasRegion("region-b"));
            assertFalse(config.hasRegion("region-c"));
            assertEquals(List.of("agent-001", "agent-002"), config.getAgentIdsForRegion("region-a"));
            assertEquals(List.of("agent-003"), config.getAgentIdsForRegion("region-b"));
        }

        @Test
        @DisplayName("仅含regionMapping的最小配置可正常解析")
        void parseMinimalConfig() {
            String json = "{\"regionMapping\":{\"r1\":[\"a1\"]}}";

            RegionRoutingConfig config = RegionRoutingConfig.fromJson(json);

            assertNotNull(config);
            assertNull(config.getSourceRegion());
            assertNull(config.getDefaultRegion());
            assertNull(config.resolveTargetRegion());
            assertTrue(config.isFallbackToBroadcast());
        }

        @Test
        @DisplayName("null输入返回null")
        void nullInputReturnsNull() {
            assertNull(RegionRoutingConfig.fromJson(null));
        }

        @Test
        @DisplayName("空白字符串输入返回null")
        void blankInputReturnsNull() {
            assertNull(RegionRoutingConfig.fromJson(""));
            assertNull(RegionRoutingConfig.fromJson("   "));
        }

        @Test
        @DisplayName("resolveTargetRegion优先使用sourceRegion")
        void resolveTargetRegionPrefersSourceRegion() {
            String json = "{\"sourceRegion\":\"src-r\",\"defaultRegion\":\"def-r\",\"regionMapping\":{}}";
            RegionRoutingConfig config = RegionRoutingConfig.fromJson(json);
            assertEquals("src-r", config.resolveTargetRegion());
        }

        @Test
        @DisplayName("resolveTargetRegion在sourceRegion为空时使用defaultRegion")
        void resolveTargetRegionUsesDefaultWhenNoSource() {
            String json = "{\"defaultRegion\":\"def-r\",\"regionMapping\":{}}";
            RegionRoutingConfig config = RegionRoutingConfig.fromJson(json);
            assertEquals("def-r", config.resolveTargetRegion());
        }

        @Test
        @DisplayName("getAgentIdsForRegion对不存在区域返回空列表")
        void getAgentIdsForNonExistentRegionReturnsEmpty() {
            String json = "{\"regionMapping\":{\"r1\":[\"a1\"]}}";
            RegionRoutingConfig config = RegionRoutingConfig.fromJson(json);
            assertTrue(config.getAgentIdsForRegion("nonexistent").isEmpty());
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
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("策略大小写不敏感")
        void strategyCaseInsensitive() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("broadcast");

            List<TargetAgentInfo> result = router.route(targets, config);
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("REGION_BASED大小写不敏感")
        void regionBasedCaseInsensitive() {
            TransferConfig config = new TransferConfig();
            config.setRoutingStrategy("region_based");
            config.setRoutingConfig("{\"sourceRegion\":\"r1\",\"regionMapping\":{\"r1\":[\"agent-001\"]}}");

            List<TargetAgentInfo> result = router.route(targets, config);
            assertEquals(1, result.size());
            assertEquals("agent-001", result.getFirst().getAgentId());
        }
    }
}
