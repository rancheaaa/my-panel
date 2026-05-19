package com.cq.agent.batch.scheduler;

import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.TransferConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class TargetRouter {

    private static final Logger log = LoggerFactory.getLogger(TargetRouter.class);

    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    public List<TargetAgentInfo> route(List<TargetAgentInfo> allTargets, TransferConfig transferConfig) {
        if (allTargets == null || allTargets.isEmpty()) {
            return List.of();
        }

        if (allTargets.size() == 1) {
            return allTargets;
        }

        String strategy = resolveRoutingStrategy(transferConfig);
        log.debug("🔀 路由策略: {}, 目标Agent数: {}", strategy, allTargets.size());

        return switch (strategy.toUpperCase()) {
            case "BROADCAST" -> allTargets;
            case "ROUND_ROBIN" -> routeRoundRobin(allTargets);
            case "RANDOM" -> routeRandom(allTargets);
            case "REGION_BASED" -> routeRegionBased(allTargets, transferConfig);
            default -> {
                log.warn("⚠️ 未知路由策略: {}, 降级为BROADCAST", strategy);
                yield allTargets;
            }
        };
    }

    private String resolveRoutingStrategy(TransferConfig transferConfig) {
        if (transferConfig == null || transferConfig.getRoutingStrategy() == null) {
            return "BROADCAST";
        }
        return transferConfig.getRoutingStrategy();
    }

    private List<TargetAgentInfo> routeRoundRobin(List<TargetAgentInfo> allTargets) {
        int index = Math.abs(roundRobinIndex.getAndIncrement()) % allTargets.size();
        TargetAgentInfo selected = allTargets.get(index);
        log.debug("🔄 ROUND_ROBIN选择: index={}, agentId={}", index, selected.getAgentId());
        return List.of(selected);
    }

    private List<TargetAgentInfo> routeRandom(List<TargetAgentInfo> allTargets) {
        int index = ThreadLocalRandom.current().nextInt(allTargets.size());
        TargetAgentInfo selected = allTargets.get(index);
        log.debug("🎲 RANDOM选择: index={}, agentId={}", index, selected.getAgentId());
        return List.of(selected);
    }

    private List<TargetAgentInfo> routeRegionBased(List<TargetAgentInfo> allTargets, TransferConfig transferConfig) {
        String routingConfigJson = transferConfig != null ? transferConfig.getRoutingConfig() : null;
        if (routingConfigJson == null || routingConfigJson.isBlank()) {
            log.warn("⚠️ REGION_BASED策略但routingConfig为空，降级为BROADCAST");
            return allTargets;
        }

        try {
            RegionRoutingConfig regionConfig = RegionRoutingConfig.fromJson(routingConfigJson);
            if (regionConfig == null || regionConfig.getRegionMapping() == null || regionConfig.getRegionMapping().isEmpty()) {
                log.warn("⚠️ REGION_BASED策略解析后regionMapping为空，降级为BROADCAST");
                return allTargets;
            }

            String targetRegion = regionConfig.resolveTargetRegion();
            if (targetRegion == null || !regionConfig.hasRegion(targetRegion)) {
                log.warn("⚠️ REGION_BASED策略目标区域不存在: targetRegion={}, availableRegions={}, 降级为BROADCAST",
                        targetRegion, regionConfig.getRegionMapping().keySet());
                return allTargets;
            }

            List<String> targetAgentIds = regionConfig.getAgentIdsForRegion(targetRegion);
            if (targetAgentIds == null || targetAgentIds.isEmpty()) {
                log.warn("⚠️ REGION_BASED策略区域无目标Agent: region={}, 降级为BROADCAST", targetRegion);
                return allTargets;
            }

            Set<String> targetIdSet = new HashSet<>(targetAgentIds);
            List<TargetAgentInfo> matched = new ArrayList<>();
            for (TargetAgentInfo target : allTargets) {
                if (targetIdSet.contains(target.getAgentId())) {
                    matched.add(target);
                }
            }

            if (!matched.isEmpty()) {
                log.debug("🌍 REGION_BASED选择: region={}, matchedAgents={}/{}, agentIds={}",
                        targetRegion, matched.size(), allTargets.size(),
                        matched.stream().map(TargetAgentInfo::getAgentId).toList());
                return matched;
            }

            if (!regionConfig.isFallbackToBroadcast()) {
                String fallback = regionConfig.getFallbackStrategy();
                log.warn("⚠️ REGION_BASED未匹配到区域Agent，使用fallback策略: {}", fallback);
                if ("ROUND_ROBIN".equalsIgnoreCase(fallback)) {
                    return routeRoundRobin(allTargets);
                }
                if ("RANDOM".equalsIgnoreCase(fallback)) {
                    return routeRandom(allTargets);
                }
            }

            log.warn("⚠️ REGION_BASED未匹配到区域Agent，降级为BROADCAST");
            return allTargets;

        } catch (Exception e) {
            log.warn("⚠️ REGION_BASED解析失败: {}, 降级为BROADCAST", e.getMessage());
            return allTargets;
        }
    }
}
