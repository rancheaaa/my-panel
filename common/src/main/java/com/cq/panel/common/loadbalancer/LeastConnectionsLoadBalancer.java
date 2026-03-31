package com.cq.panel.common.loadbalancer;

import java.util.List;
import java.util.Comparator;

/**
 * 最少连接负载均衡算法
 */
public class LeastConnectionsLoadBalancer implements LoadBalancer {
    
    private static final String NAME = "leastConnections";

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

        // 选择并发连接数最少的服务器
        Server selected = aliveServers.stream()
                .min(Comparator.comparingInt(Server::getConcurrentRequests))
                .orElse(null);

        selected.setLastAccessTime(System.currentTimeMillis());

        return selected;
    }

    @Override
    public Server choose(List<Server> servers, Object key) {
        // 最少连接算法不使用key，直接调用无参方法
        return choose(servers);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void notifyServerStatus(Server server, ServerStatus status) {
        // 最少连接算法不需要特殊的状态处理
    }
}