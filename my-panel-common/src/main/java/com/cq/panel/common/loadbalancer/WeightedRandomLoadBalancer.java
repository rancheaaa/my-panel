package com.cq.panel.common.loadbalancer;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 加权随机负载均衡算法
 */
public class WeightedRandomLoadBalancer implements LoadBalancer {
    
    private static final String NAME = "weightedRandom";

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

        // 计算总权重
        int totalWeight = aliveServers.stream()
                .mapToInt(this::calculateWeight)
                .sum();

        if (totalWeight == 0) {
            // 如果所有服务器权重都为0，使用普通随机
            int index = ThreadLocalRandom.current().nextInt(aliveServers.size());
            Server selected = aliveServers.get(index);
            selected.setLastAccessTime(System.currentTimeMillis());
            return selected;
        }

        // 生成随机数并选择服务器
        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int weightSum = 0;
        
        for (Server server : aliveServers) {
            int weight = calculateWeight(server);
            weightSum += weight;
            if (random < weightSum) {
                server.setLastAccessTime(System.currentTimeMillis());
                return server;
            }
        }

        // 如果计算失败，返回第一个可用服务器
        Server selected = aliveServers.get(0);
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
        // 加权随机算法不需要特殊的状态处理
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