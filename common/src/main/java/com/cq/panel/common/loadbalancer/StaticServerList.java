package com.cq.panel.common.loadbalancer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 静态服务器列表实现
 * 通过配置文件或代码配置服务器列表
 */
public class StaticServerList implements ServerList {
    
    private final ConcurrentMap<String, List<Server>> serverMap;

    public StaticServerList() {
        this(new HashMap<>());
    }

    public StaticServerList(Map<String, List<Server>> serverMap) {
        this.serverMap = new ConcurrentHashMap<>(serverMap);
    }

    @Override
    public List<Server> getServers(String serviceName) {
        return serverMap.getOrDefault(serviceName, Collections.emptyList());
    }

    @Override
    public List<Server> getAllServers() {
        return serverMap.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    @Override
    public List<Server> getUpServers(String serviceName) {
        return getServers(serviceName).stream()
                .filter(Server::isAlive)
                .collect(Collectors.toList());
    }

    @Override
    public List<Server> getDownServers(String serviceName) {
        return getServers(serviceName).stream()
                .filter(server ->  !server.isAlive())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getAllServiceNames() {
        return new ArrayList<>(serverMap.keySet());
    }

    @Override
    public void refresh() {
        // 静态服务器列表不需要刷新
    }

    /**
     * 添加服务服务器
     * 
     * @param serviceName 服务名
     * @param servers 服务器列表
     */
    public void addServers(String serviceName, List<Server> servers) {
        if (serviceName == null) {
            throw new IllegalArgumentException("serviceName cannot be null");
        }
        if (servers == null) {
            throw new IllegalArgumentException("servers cannot be null");
        }
        // 不再修改服务器ID，保持原始ID
        serverMap.put(serviceName, new ArrayList<>(servers));
    }

    /**
     * 添加单个服务器
     * 
     * @param serviceName 服务名
     * @param server 服务器
     */
    public void addServer(String serviceName, Server server) {
        if (serviceName == null) {
            throw new IllegalArgumentException("serviceName is null");
        }
        if (server == null) {
            throw new IllegalArgumentException("server cannot be null");
        }
        // 不再修改服务器ID，保持原始ID
        serverMap.compute(serviceName, (key, existingServers) -> {
            List<Server> servers = existingServers != null ? 
                    new ArrayList<>(existingServers) : new ArrayList<>();
            servers.add(server);
            return servers;
        });
    }

    /**
     * 移除服务
     * 
     * @param serviceName 服务名
     */
    public void removeService(String serviceName) {
        if (serviceName == null) {
            throw new IllegalArgumentException("serviceName is null");
        }
        serverMap.remove(serviceName);
    }

    /**
     * 移除服务器
     * 
     * @param serviceName 服务名
     * @param server 服务器
     */
    public void removeServer(String serviceName, Server server) {
        if (serviceName == null) {
            throw new IllegalArgumentException("serviceName is null");
        }
        if (server == null) {
            throw new IllegalArgumentException("server cannot be null");
        }
        serverMap.computeIfPresent(serviceName, (key, existingServers) -> {
            List<Server> servers = new ArrayList<>(existingServers);
            servers.remove(server);
            return servers.isEmpty() ? null : servers;
        });
    }

    /**
     * 清空所有服务器
     */
    public void clear() {
        serverMap.clear();
    }
}