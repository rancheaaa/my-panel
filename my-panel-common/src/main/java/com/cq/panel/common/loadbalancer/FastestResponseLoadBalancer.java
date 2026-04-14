package com.cq.panel.common.loadbalancer;

import java.util.List;
import java.util.Comparator;

/**
 * 最快响应负载均衡算法
 */
public class FastestResponseLoadBalancer implements LoadBalancer {
    
    private static final String NAME = "fastestResponse";

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

        // 选择响应时间最短的服务器
        Server selected = aliveServers.stream()
                .min(Comparator.comparingLong(Server::getResponseTime))
                .orElse(null);

        if (selected != null) {
            selected.setLastAccessTime(System.currentTimeMillis());
        }
        
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
        // 最快响应算法不需要特殊的状态处理
    }
}