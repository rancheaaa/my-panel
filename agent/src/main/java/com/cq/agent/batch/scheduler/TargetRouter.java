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
            Map<String, Object> configMap = parseSimpleJson(routingConfigJson);
            @SuppressWarnings("unchecked")
            Map<String, Object> regionMapping = (Map<String, Object>) configMap.get("regionMapping");
            String defaultRegion = (String) configMap.get("defaultRegion");

            if (regionMapping == null || regionMapping.isEmpty()) {
                log.warn("⚠️ REGION_BASED策略但regionMapping为空，降级为BROADCAST");
                return allTargets;
            }

            String sourceRegion = findSourceRegion(regionMapping);
            if (sourceRegion == null) {
                sourceRegion = defaultRegion;
            }

            if (sourceRegion != null && regionMapping.containsKey(sourceRegion)) {
                @SuppressWarnings("unchecked")
                List<String> regionAgentIds = (List<String>) regionMapping.get(sourceRegion);
                List<TargetAgentInfo> matched = new ArrayList<>();
                for (TargetAgentInfo target : allTargets) {
                    if (regionAgentIds.contains(target.getAgentId())) {
                        matched.add(target);
                    }
                }
                if (!matched.isEmpty()) {
                    log.debug("🌍 REGION_BASED选择: region={}, agents={}", sourceRegion,
                            matched.stream().map(TargetAgentInfo::getAgentId).toList());
                    return matched;
                }
            }

            log.warn("⚠️ REGION_BASED未匹配到区域Agent，降级为BROADCAST");
            return allTargets;

        } catch (Exception e) {
            log.warn("⚠️ REGION_BASED解析失败: {}, 降级为BROADCAST", e.getMessage());
            return allTargets;
        }
    }

    private String findSourceRegion(Map<String, Object> regionMapping) {
        return null;
    }

    private Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }

        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        int depth = 0;
        StringBuilder current = new StringBuilder();
        List<String> pairs = new ArrayList<>();

        for (char c : trimmed.toCharArray()) {
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
            else if (c == ',' && depth == 0) {
                pairs.add(current.toString().trim());
                current = new StringBuilder();
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            pairs.add(current.toString().trim());
        }

        for (String pair : pairs) {
            int colonIdx = pair.indexOf(':');
            if (colonIdx < 0) continue;

            String key = pair.substring(0, colonIdx).trim().replace("\"", "");
            String value = pair.substring(colonIdx + 1).trim();

            if (value.startsWith("{") || value.startsWith("[")) {
                result.put(key, value);
            } else if (value.startsWith("\"")) {
                result.put(key, value.replace("\"", ""));
            } else if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                result.put(key, Boolean.parseBoolean(value));
            } else {
                try {
                    if (value.contains(".")) {
                        result.put(key, Double.parseDouble(value));
                    } else {
                        result.put(key, Long.parseLong(value));
                    }
                } catch (NumberFormatException e) {
                    result.put(key, value);
                }
            }
        }

        return result;
    }
}
