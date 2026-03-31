package com.cq.panel.common.loadbalancer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 加权轮询负载均衡算法
 */
public class WeightedRoundRobinLoadBalancer implements LoadBalancer {
    
    private static final String NAME = "weightedRoundRobin";
    private final AtomicInteger position = new AtomicInteger(0);

    @Override
    public Server choose(List<Server> servers) {
        if (servers == null || servers.isEmpty()) {
            return null;
        }

        List<Server> aliveServers = servers.stream()
                .filter(Server::isAlive)
                .toList();

        if (aliveServers.isEmpty()) {
            return null;
        }

        // 简单的加权轮询实现：根据响应时间计算权重
        // 响应时间越短，权重越高
        int totalWeight = aliveServers.stream()
                .mapToInt(this::calculateWeight)
                .sum();

        if (totalWeight == 0) {
            // 如果所有服务器权重都为0，使用普通轮询
            int next = Math.abs(position.getAndIncrement() % aliveServers.size());
            Server selected = aliveServers.get(next);
            selected.setLastAccessTime(System.currentTimeMillis());
            return selected;
        }

        int current = Math.abs(position.getAndIncrement() % totalWeight);
        int weightSum = 0;
        
        for (Server server : aliveServers) {
            int weight = calculateWeight(server);
            weightSum += weight;
            if (current < weightSum) {
                server.setLastAccessTime(System.currentTimeMillis());
                return server;
            }
        }

        // 如果计算失败，返回第一个可用服务器
        Server selected = aliveServers.getFirst();
        selected.setLastAccessTime(System.currentTimeMillis());
        return selected;
    }

    @Override
    public Server choose(List<Server> servers, Object key) {
        return choose(servers);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void notifyServerStatus(Server server, ServerStatus status) {
        // 加权轮询算法不需要特殊的状态处理
    }

    /**
     * 计算服务器权重
     * 响应时间越短，权重越高
     */
    private int calculateWeight(Server server) {
        if (server.getResponseTime() <= 0) {
            return 10; // 默认权重
        }
        
        // 响应时间在100ms以内，权重为100
        // 响应时间每增加100ms，权重减少10
        long responseTime = server.getResponseTime();
        int weight = (int) Math.max(1, 100 - (responseTime / 100) * 10);
        return Math.max(1, weight);
    }
}