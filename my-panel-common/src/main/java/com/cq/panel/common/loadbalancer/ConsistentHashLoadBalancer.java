package com.cq.panel.common.loadbalancer;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 一致性哈希负载均衡算法
 */
public class ConsistentHashLoadBalancer implements LoadBalancer {
    
    private static final String NAME = "consistentHash";
    private static final int VIRTUAL_NODES = 160; // 虚拟节点数
    private final ConcurrentMap<String, SortedMap<Integer, Server>> ringCache = new ConcurrentHashMap<>();

    @Override
    public Server choose(List<Server> servers) {
        // 一致性哈希算法需要key，如果没有key则使用随机算法
        return choose(servers, System.currentTimeMillis());
    }

    @Override
    public Server choose(List<Server> servers, Object key) {
        if (servers == null || servers.isEmpty()) {
            return null;
        }

        List<Server> aliveServers = servers.stream()
                .filter(Server::isAlive)
                .toList();

        if (aliveServers.isEmpty()) {
            return null;
        }

        // 如果只有一个服务器，直接返回
        if (aliveServers.size() == 1) {
            Server selected = aliveServers.get(0);
            selected.setLastAccessTime(System.currentTimeMillis());
            return selected;
        }

        // 获取或创建一致性哈希环
        SortedMap<Integer, Server> ring = getConsistentHashRing(aliveServers);
        
        // 计算key的哈希值
        int hash = Math.abs(key.hashCode());
        
        // 在环上查找对应的服务器
        SortedMap<Integer, Server> tailMap = ring.tailMap(hash);
        Server selected = tailMap.isEmpty() ? ring.get(ring.firstKey()) : tailMap.get(tailMap.firstKey());
        
        if (selected != null) {
            selected.setLastAccessTime(System.currentTimeMillis());
        }
        
        return selected;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void notifyServerStatus(Server server, ServerStatus status) {
        // 服务器状态变化时清除缓存
        ringCache.clear();
    }

    /**
     * 获取一致性哈希环
     */
    private SortedMap<Integer, Server> getConsistentHashRing(List<Server> servers) {
        String cacheKey = generateCacheKey(servers);
        return ringCache.computeIfAbsent(cacheKey, k -> buildConsistentHashRing(servers));
    }

    /**
     * 构建一致性哈希环
     */
    private SortedMap<Integer, Server> buildConsistentHashRing(List<Server> servers) {
        SortedMap<Integer, Server> ring = new TreeMap<>();
        
        for (Server server : servers) {
            // 为每个物理节点添加虚拟节点
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                String virtualNode = server.getId() + "#" + i;
                int hash = Math.abs(virtualNode.hashCode());
                ring.put(hash, server);
            }
        }
        
        return ring;
    }

    /**
     * 生成缓存键
     */
    private String generateCacheKey(List<Server> servers) {
        StringBuilder sb = new StringBuilder();
        for (Server server : servers) {
            sb.append(server.getId()).append(";");
        }
        return sb.toString();
    }
}