package com.cq.panel.common.loadbalancer;

/**
 * 负载均衡器工厂
 */
public class LoadBalancerFactory {

    /**
     * 根据算法创建负载均衡器
     * 
     * @param algorithm 负载均衡算法
     * @return 负载均衡器实例
     */
    public static LoadBalancer createLoadBalancer(LoadBalancerAlgorithm algorithm) {
        return createLoadBalancer(algorithm.getName());
    }

    /**
     * 根据算法名称创建负载均衡器
     * 
     * @param algorithmName 算法名称
     * @return 负载均衡器实例
     */
    public static LoadBalancer createLoadBalancer(String algorithmName) {
        LoadBalancerAlgorithm algorithm = LoadBalancerAlgorithm.fromName(algorithmName);

        return switch (algorithm) {
            case ROUND_ROBIN -> new RoundRobinLoadBalancer();
            case RANDOM -> new RandomLoadBalancer();
            case LEAST_CONNECTIONS -> new LeastConnectionsLoadBalancer();
            case WEIGHTED_ROUND_ROBIN -> new WeightedRoundRobinLoadBalancer();
            case WEIGHTED_RANDOM -> new WeightedRandomLoadBalancer();
            case FASTEST_RESPONSE -> new FastestResponseLoadBalancer();
            case CONSISTENT_HASH -> new ConsistentHashLoadBalancer();
            case ZONE_AWARE -> new ZoneAwareLoadBalancer();
        };
    }
}