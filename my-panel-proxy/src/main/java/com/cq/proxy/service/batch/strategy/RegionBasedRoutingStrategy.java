package com.cq.proxy.service.batch.strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RegionBasedRoutingStrategy implements RoutingStrategy
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Map<String, List<String>> regionAgents;
    private final List<RegionRule> regionRules;
    private final String defaultRegion;
    private final SingleRoutingStrategy fallback = new SingleRoutingStrategy();

    public RegionBasedRoutingStrategy(String routingConfig)
    {
        Map<String, Object> config = parseConfig(routingConfig);
        this.defaultRegion = String.valueOf(config.getOrDefault("defaultRegion", "default"));
        this.regionAgents = parseRegionAgents(config.get("regionAgents"), config.get("regions"));
        this.regionRules = parseRegionRules(config.get("rules"));
    }

    @Override
    public String selectTarget(String filePath, List<String> candidates, Map<String, Object> context)
    {
        if (candidates == null || candidates.isEmpty())
        {
            return null;
        }
        String matchedRegion = matchRegion(filePath);
        List<String> regionScopedCandidates = filterCandidatesByRegion(candidates, matchedRegion);
        if (regionScopedCandidates.isEmpty() && defaultRegion != null)
        {
            regionScopedCandidates = filterCandidatesByRegion(candidates, defaultRegion);
        }
        if (regionScopedCandidates.isEmpty())
        {
            regionScopedCandidates = candidates;
        }
        return fallback.selectTarget(filePath, regionScopedCandidates, context);
    }

    private Map<String, Object> parseConfig(String routingConfig)
    {
        if (routingConfig == null || routingConfig.isBlank())
        {
            return Collections.emptyMap();
        }
        try
        {
            return OBJECT_MAPPER.readValue(routingConfig, new TypeReference<>() {});
        }
        catch (Exception ignore)
        {
            return Collections.emptyMap();
        }
    }

    private Map<String, List<String>> parseRegionAgents(Object primary, Object fallbackValue)
    {
        Object source = primary != null ? primary : fallbackValue;
        if (!(source instanceof Map<?, ?> sourceMap))
        {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : sourceMap.entrySet())
        {
            if (entry.getKey() == null)
            {
                continue;
            }
            List<String> agents = toStringList(entry.getValue());
            if (!agents.isEmpty())
            {
                result.put(String.valueOf(entry.getKey()), agents);
            }
        }
        return result;
    }

    private List<RegionRule> parseRegionRules(Object rulesObj)
    {
        if (!(rulesObj instanceof List<?> rawRules))
        {
            return Collections.emptyList();
        }
        List<RegionRule> rules = new ArrayList<>();
        for (Object rawRule : rawRules)
        {
            if (!(rawRule instanceof Map<?, ?> rawMap))
            {
                continue;
            }
            Object pattern = rawMap.get("pattern");
            Object region = rawMap.get("region");
            if (pattern == null || region == null)
            {
                continue;
            }
            rules.add(new RegionRule(String.valueOf(pattern), String.valueOf(region)));
        }
        return rules;
    }

    private List<String> filterCandidatesByRegion(List<String> candidates, String region)
    {
        if (region == null)
        {
            return Collections.emptyList();
        }
        List<String> scopedAgents = regionAgents.get(region);
        if (scopedAgents == null || scopedAgents.isEmpty())
        {
            return Collections.emptyList();
        }
        return scopedAgents.stream()
                .filter(candidates::contains)
                .collect(Collectors.toList());
    }

    private String matchRegion(String filePath)
    {
        for (RegionRule rule : regionRules)
        {
            if (rule.matches(filePath))
            {
                return rule.region();
            }
        }
        return defaultRegion;
    }

    private List<String> toStringList(Object value)
    {
        if (!(value instanceof List<?> rawList))
        {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : rawList)
        {
            if (item != null)
            {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private record RegionRule(String pattern, String region)
    {
        boolean matches(String filePath)
        {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern.replace('\\', '/'));
            return matcher.matches(Paths.get(filePath.replace('\\', '/')));
        }
    }
}
