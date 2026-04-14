package com.cq.panel.common.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 负载均衡管理器
 * 负责管理多个负载均衡客户端
 */
public class LoadBalancerManager {
    
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerManager.class);
    
    private final ConcurrentMap<String, LoadBalancerClient> clientMap;
    private final LoadBalancerConfig defaultConfig;
    private final HttpClient httpClient;

    public LoadBalancerManager() {
        this(LoadBalancerConfig.defaultConfig());
    }

    public LoadBalancerManager(LoadBalancerConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
        this.clientMap = new ConcurrentHashMap<>();
        this.httpClient = createHttpClient(defaultConfig);
    }

    /**
     * 获取或创建负载均衡客户端
     * 
     * @param serviceName 服务名
     * @param serverList 服务器列表
     * @return 负载均衡客户端
     */
    public LoadBalancerClient getClient(String serviceName, ServerList serverList) {
        return getClient(serviceName, serverList, defaultConfig.getAlgorithm());
    }

    /**
     * 获取或创建负载均衡客户端
     * 
     * @param serviceName 服务名
     * @param serverList 服务器列表
     * @param algorithm 负载均衡算法
     * @return 负载均衡客户端
     */
    public LoadBalancerClient getClient(String serviceName, ServerList serverList, LoadBalancerAlgorithm algorithm) {
        String clientKey = serviceName + "-" + algorithm.getName();
        
        return clientMap.computeIfAbsent(clientKey, key -> {
            LoadBalancer loadBalancer = LoadBalancerFactory.createLoadBalancer(algorithm);
            
            // 如果是区域感知算法，设置本地区域
            if (loadBalancer instanceof ZoneAwareLoadBalancer) {
                loadBalancer = new ZoneAwareLoadBalancer(defaultConfig.getLocalZone());
            }
            
            // 使用工厂创建健康检查器
            HealthChecker healthChecker = HealthCheckerFactory.create(defaultConfig, defaultConfig.getHealthCheckerType());
            
            logger.info("Created load balancer client for service: {}, algorithm: {}, health checker: {}", 
                    serviceName, algorithm.getName(), healthChecker.getName());
            return new LoadBalancerClient(loadBalancer, serverList, httpClient, healthChecker, defaultConfig);
        });
    }

    /**
     * 移除负载均衡客户端
     * 
     * @param serviceName 服务名
     */
    public void removeClient(String serviceName) {
        clientMap.entrySet().removeIf(entry -> entry.getKey().startsWith(serviceName + "-"));
        logger.info("Removed load balancer client for service: {}", serviceName);
    }

    /**
     * 获取所有负载均衡客户端
     * 
     * @return 客户端映射
     */
    public Map<String, LoadBalancerClient> getAllClients() {
        return Map.copyOf(clientMap);
    }

    /**
     * 清空所有负载均衡客户端
     */
    public void clear() {
        clientMap.clear();
        logger.info("Cleared all load balancer clients");
    }

    /**
     * 获取默认配置
     */
    public LoadBalancerConfig getDefaultConfig() {
        return defaultConfig;
    }

    /**
     * 获取HTTP客户端
     */
    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * 创建HTTP客户端
     */
    private HttpClient createHttpClient(LoadBalancerConfig config) {
        // 默认使用Apache HttpClient，提供更好的性能和功能
        return new ApacheHttpClient(config.getConnectTimeout(), config.getReadTimeout());
    }

    /**
     * 创建默认管理器
     */
    public static LoadBalancerManager createDefault() {
        return new LoadBalancerManager();
    }
}