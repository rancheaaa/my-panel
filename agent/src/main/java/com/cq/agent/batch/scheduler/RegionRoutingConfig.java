package com.cq.agent.batch.scheduler;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.List;
import java.util.Map;

public class RegionRoutingConfig {

    private Map<String, List<String>> regionMapping;
    private String sourceRegion;
    private String defaultRegion;
    private String fallbackStrategy;

    public static RegionRoutingConfig fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Gson gson = new Gson();
        return gson.fromJson(json, RegionRoutingConfig.class);
    }

    public String resolveTargetRegion() {
        if (sourceRegion != null && !sourceRegion.isBlank()) {
            return sourceRegion;
        }
        return defaultRegion;
    }

    public boolean hasRegion(String region) {
        return regionMapping != null && regionMapping.containsKey(region);
    }

    public List<String> getAgentIdsForRegion(String region) {
        if (regionMapping == null || region == null || !regionMapping.containsKey(region)) {
            return List.of();
        }
        return regionMapping.get(region);
    }

    public boolean isFallbackToBroadcast() {
        return fallbackStrategy == null
                || fallbackStrategy.isBlank()
                || "BROADCAST".equalsIgnoreCase(fallbackStrategy);
    }

    public Map<String, List<String>> getRegionMapping() {
        return regionMapping;
    }

    public void setRegionMapping(Map<String, List<String>> regionMapping) {
        this.regionMapping = regionMapping;
    }

    public String getSourceRegion() {
        return sourceRegion;
    }

    public void setSourceRegion(String sourceRegion) {
        this.sourceRegion = sourceRegion;
    }

    public String getDefaultRegion() {
        return defaultRegion;
    }

    public void setDefaultRegion(String defaultRegion) {
        this.defaultRegion = defaultRegion;
    }

    public String getFallbackStrategy() {
        return fallbackStrategy;
    }

    public void setFallbackStrategy(String fallbackStrategy) {
        this.fallbackStrategy = fallbackStrategy;
    }
}
