package com.cq.panel.common.loadbalancer;

import java.util.List;

/**
 * 负载均衡器接口
 * 提供服务器选择功能，模仿Ribbon的核心功能
 */
public interface LoadBalancer {

    /**
     * 选择服务器
     * 
     * @param servers 服务器列表
     * @return 选择的服务器，如果没有可用服务器返回null
     */
    Server choose(List<Server> servers);

    /**
     * 选择服务器（带负载均衡键）
     * 
     * @param servers 服务器列表
     * @param key 负载均衡键（用于一致性哈希等算法）
     * @return 选择的服务器，如果没有可用服务器返回null
     */
    Server choose(List<Server> servers, Object key);

    /**
     * 获取负载均衡器名称
     * 
     * @return 负载均衡器名称
     */
    String getName();

    /**
     * 服务器状态更新通知
     * 
     * @param server 服务器
     * @param status 状态
     */
    void notifyServerStatus(Server server, ServerStatus status);
}