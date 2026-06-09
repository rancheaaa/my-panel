package com.cq.panel.admin.server.service.proxy;

import com.cq.panel.common.loadbalancer.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Proxy服务器列表管理
 * 支持逗号分隔的多URL配置，轮询选择，存活状态管理
 */
public class ProxyServerList {

    private static final Logger log = LoggerFactory.getLogger(ProxyServerList.class);

    private final List<Server> servers;
    private final AtomicInteger roundRobinIndex;

    public ProxyServerList(List<Server> servers) {
        this.servers = Collections.synchronizedList(new ArrayList<>(servers));
        this.roundRobinIndex = new AtomicInteger(0);
    }

    /**
     * 从逗号分隔的URL字符串解析创建ProxyServerList
     *
     * @param urls 逗号分隔的URL，如 "<a href="http://10.0.0.1:9876,http://10.0.0.2:9876">...</a>"
     * @return ProxyServerList实例
     */
    public static ProxyServerList fromUrls(String urls) {
        if (urls == null || urls.isBlank()) {
            return new ProxyServerList(Collections.emptyList());
        }

        // 去重
        Set<String> uniqueUrls = Arrays.stream(urls.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Server> servers = new ArrayList<>();
        for (String url : uniqueUrls) {
            try {
                URI uri = new URI(url);
                String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 9876;
                if (host != null) {
                    servers.add(new Server(null, host, port, scheme, null));
                }
            } catch (Exception e) {
                log.warn("解析Proxy URL失败: {} - {}", url, e.getMessage());
            }
        }

        return new ProxyServerList(servers);
    }

    /**
     * 轮询选择下一个Server
     * 优先选择存活的Server；所有Server宕机时降级轮询所有（给恢复机会）
     *
     * @return 选中的Server，空列表返回null
     */
    public Server next() {
        if (servers.isEmpty()) {
            return null;
        }

        List<Server> aliveServers = getAliveServers();
        List<Server> candidates = aliveServers.isEmpty() ? servers : aliveServers;

        int index = Math.abs(roundRobinIndex.getAndIncrement() % candidates.size());
        return candidates.get(index);
    }

    /**
     * 获取所有Server
     */
    public List<Server> getServers() {
        return Collections.unmodifiableList(servers);
    }

    /**
     * 获取所有存活Server
     */
    public List<Server> getAliveServers() {
        return servers.stream()
                .filter(Server::isAlive)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有宕机Server
     */
    public List<Server> getDownServers() {
        return servers.stream()
                .filter(s -> !s.isAlive())
                .collect(Collectors.toList());
    }

    /**
     * 标记Server为存活
     */
    public void markAlive(Server server) {
        if (server != null && !server.isAlive()) {
            server.setAlive(true);
            log.info("Proxy Server恢复: {}", server.getUrl());
        }
    }

    /**
     * 标记Server为宕机
     */
    public void markDown(Server server) {
        if (server != null && server.isAlive()) {
            server.setAlive(false);
            log.warn("Proxy Server宕机: {}", server.getUrl());
        }
    }

    /**
     * 是否为空列表
     */
    public boolean isEmpty() {
        return servers.isEmpty();
    }

    /**
     * 获取Server数量
     */
    public int size() {
        return servers.size();
    }
}
