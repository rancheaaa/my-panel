package com.cq.panel.common.loadbalancer;

/**
 * 负载均衡算法枚举
 */
public enum LoadBalancerAlgorithm {
    /**
     * 轮询算法
     */
    ROUND_ROBIN("roundRobin"),
    
    /**
     * 随机算法
     */
    RANDOM("random"),
    
    /**
     * 加权轮询算法
     */
    WEIGHTED_ROUND_ROBIN("weightedRoundRobin"),
    
    /**
     * 加权随机算法
     */
    WEIGHTED_RANDOM("weightedRandom"),
    
    /**
     * 最少连接算法
     */
    LEAST_CONNECTIONS("leastConnections"),
    
    /**
     * 最快响应算法
     */
    FASTEST_RESPONSE("fastestResponse"),
    
    /**
     * 一致性哈希算法
     */
    CONSISTENT_HASH("consistentHash"),
    
    /**
     * 区域感知算法
     */
    ZONE_AWARE("zoneAware");

    private final String name;

    LoadBalancerAlgorithm(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static LoadBalancerAlgorithm fromName(String name) {
        for (LoadBalancerAlgorithm algorithm : values()) {
            if (algorithm.name.equalsIgnoreCase(name)) {
                return algorithm;
            }
        }
        return ROUND_ROBIN; // 默认使用轮询算法
    }
}