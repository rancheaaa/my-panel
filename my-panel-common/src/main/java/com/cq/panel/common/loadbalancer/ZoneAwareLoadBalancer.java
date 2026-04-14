package com.cq.panel.common.loadbalancer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 区域感知负载均衡算法
 * 优先选择同一区域的服务器，如果没有则选择其他区域的服务器
 */
public class ZoneAwareLoadBalancer implements LoadBalancer {
    
    private static final String NAME = "zoneAware";
    private final String localZone; // 本地区域
    private final ConcurrentMap<String, Map<String, List<Server>>> zoneCache = new ConcurrentHashMap<>();

    public ZoneAwareLoadBalancer() {
        this.localZone = "default"; // 默认区域
    }

    public ZoneAwareLoadBalancer(String localZone) {
        this.localZone = localZone != null ? localZone : "default";
    }

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

        // 按区域分组
        Map<String, List<Server>> zoneServers = groupServersByZone(aliveServers);
        
        // 优先选择本地区域的服务器
        List<Server> localZoneServers = zoneServers.get(localZone);
        if (localZoneServers != null && !localZoneServers.isEmpty()) {
            return chooseFromServers(localZoneServers);
        }

        // 如果没有本地区域服务器，选择其他区域的服务器
        for (Map.Entry<String, List<Server>> entry : zoneServers.entrySet()) {
            if (!entry.getKey().equals(localZone) && !entry.getValue().isEmpty()) {
                return chooseFromServers(entry.getValue());
            }
        }

        // 如果所有区域都没有可用服务器，返回null
        return null;
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
        // 服务器状态变化时清除缓存
        zoneCache.clear();
    }

    /**
     * 从服务器列表中选择一个服务器（使用随机算法）
     */
    private Server chooseFromServers(List<Server> servers) {
        int index = ThreadLocalRandom.current().nextInt(servers.size());
        Server selected = servers.get(index);
        selected.setLastAccessTime(System.currentTimeMillis());
        return selected;
    }

    /**
     * 按区域分组服务器
     */
    private Map<String, List<Server>> groupServersByZone(List<Server> servers) {
        String cacheKey = generateCacheKey(servers);
        return zoneCache.computeIfAbsent(cacheKey, k -> servers.stream().collect(Collectors.groupingBy(
            server -> server.getZone() != null ? server.getZone() : "default"
        )));
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