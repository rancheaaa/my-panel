package com.cq.panel.common.loadbalancer;

/**
 * 健康检查接口
 * 用于检测服务器是否可用
 */
public interface HealthChecker {
    
    /**
     * 检查服务器健康状态
     * 
     * @param server 要检查的服务器
     * @return true表示健康可用，false表示不可用
     */
    boolean isHealthy(Server server);
    
    /**
     * 获取健康检查器名称
     * 
     * @return 健康检查器名称
     */
    String getName();
}