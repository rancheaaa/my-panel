package com.cq.panel.common.loadbalancer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡算法
 */
public class RoundRobinLoadBalancer implements LoadBalancer {
    
    private static final String NAME = "roundRobin";
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

        int next = Math.abs(position.getAndIncrement() % aliveServers.size());
        Server selected = aliveServers.get(next);
        selected.setLastAccessTime(System.currentTimeMillis());
        
        return selected;
    }

    @Override
    public Server choose(List<Server> servers, Object key) {
        // 轮询算法不使用key，直接调用无参方法
        return choose(servers);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void notifyServerStatus(Server server, ServerStatus status) {
        // 轮询算法不需要特殊的状态处理
    }
}