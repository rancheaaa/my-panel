package com.cq.panel.common.loadbalancer;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机负载均衡算法
 */
public class RandomLoadBalancer implements LoadBalancer {
    
    private static final String NAME = "random";

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

        int index = ThreadLocalRandom.current().nextInt(aliveServers.size());
        Server selected = aliveServers.get(index);
        selected.setLastAccessTime(System.currentTimeMillis());
        
        return selected;
    }

    @Override
    public Server choose(List<Server> servers, Object key) {
        // 随机算法不使用key，直接调用无参方法
        return choose(servers);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void notifyServerStatus(Server server, ServerStatus status) {
        // 随机算法不需要特殊的状态处理
    }
}