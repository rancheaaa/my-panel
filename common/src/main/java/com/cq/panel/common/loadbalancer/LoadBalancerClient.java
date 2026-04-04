package com.cq.panel.common.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 负载均衡客户端
 * 模仿Ribbon的LoadBalancerClient接口
 */
public class LoadBalancerClient {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerClient.class);

    private final LoadBalancer loadBalancer;
    private final HttpClient httpClient;
    private final ServerList serverList;
    private final HealthChecker healthChecker;
    private final LoadBalancerConfig loadBalancerConfig;
    private final ScheduledExecutorService healthCheckScheduler;

    public LoadBalancerClient(LoadBalancer loadBalancer, ServerList serverList) {
        this(loadBalancer, serverList, new SimpleHttpClient(), new TcpHealthChecker(), LoadBalancerConfig.defaultConfig());
    }

    public LoadBalancerClient(LoadBalancer loadBalancer, ServerList serverList, HttpClient httpClient) {
        this(loadBalancer, serverList, httpClient, new TcpHealthChecker(), LoadBalancerConfig.defaultConfig());
    }

    public LoadBalancerClient(LoadBalancer loadBalancer, ServerList serverList, HealthChecker healthChecker) {
        this(loadBalancer, serverList, new SimpleHttpClient(), healthChecker, LoadBalancerConfig.defaultConfig());
    }

    public LoadBalancerClient(LoadBalancer loadBalancer, ServerList serverList, LoadBalancerConfig loadBalancerConfig) {
        this(loadBalancer, serverList, new SimpleHttpClient(), new TcpHealthChecker(), loadBalancerConfig);
    }

    public LoadBalancerClient(LoadBalancer loadBalancer, ServerList serverList, HttpClient httpClient, LoadBalancerConfig loadBalancerConfig) {
        this(loadBalancer, serverList, httpClient, new TcpHealthChecker(), loadBalancerConfig);
    }

    public LoadBalancerClient(LoadBalancer loadBalancer, ServerList serverList, HttpClient httpClient,
                              HealthChecker healthChecker, LoadBalancerConfig loadBalancerConfig) {
        this.loadBalancer = loadBalancer;
        this.serverList = serverList;
        this.httpClient = httpClient;
        this.healthChecker = healthChecker;
        this.loadBalancerConfig = loadBalancerConfig;

        // 启动健康检查调度器
        if (loadBalancerConfig.isHealthCheckEnabled()) {
            // 手动创建单线程调度器
            this.healthCheckScheduler = new ScheduledThreadPoolExecutor(1, r -> {
                Thread t = new Thread(r, "health-check-scheduler");
                t.setDaemon(true);
                return t;
            });
            startHealthCheck();
        } else {
            this.healthCheckScheduler = null;
        }
    }

    public LoadBalancerClient(LoadBalancerAlgorithm algorithm) {
        this(LoadBalancerFactory.createLoadBalancer(algorithm), new StaticServerList(), new SimpleHttpClient(), new TcpHealthChecker(), LoadBalancerConfig.defaultConfig());
    }

    /**
     * 启动健康检查
     */
    private void startHealthCheck() {
        if (loadBalancerConfig.isHealthCheckEnabled() && healthCheckScheduler != null) {
            healthCheckScheduler.scheduleAtFixedRate(() -> performHealthCheck(false),
                    1000, loadBalancerConfig.getHealthCheckInterval(), TimeUnit.MILLISECONDS);
            logger.info("健康检查已启动，间隔: {}ms", loadBalancerConfig.getHealthCheckInterval());
        }
        performHealthCheck(true);
    }

    /**
     * 执行健康检查
     */
    private void performHealthCheck(boolean fastCheck) {
        try {
            // 获取所有服务列表
            List<String> serviceNames = serverList.getAllServiceNames();
            if (serviceNames == null || serviceNames.isEmpty()) {
                return;
            }

            for (String serviceName : serviceNames) {
                List<Server> servers = serverList.getServers(serviceName);
                if (servers == null || servers.isEmpty()) {
                    continue;
                }

                for (Server server : servers) {
                    // 检查是否需要健康检查
                    if (server.needsHealthCheck(loadBalancerConfig.getHealthCheckInterval())) {
                        boolean healthy = healthChecker.isHealthy(server);

                        if (healthy) {
                            server.recordHealthCheckSuccess();
                        } else {
                            server.recordHealthCheckFailure();
                        }

                        if (fastCheck) {
                            server.setAlive(healthy);
                        } else {
                            // 更新服务器状态
                            server.updateHealthStatus(
                                    loadBalancerConfig.getHealthCheckFailureThreshold(),
                                    loadBalancerConfig.getHealthCheckSuccessThreshold()
                            );

                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("健康检查异常", e);
        }
    }

    /**
     * 从URL数组中选择一个URL（用于定时任务负载均衡）
     *
     * @param request 负载均衡请求
     * @return 选中的URL
     */
    public String choose(LoadBalancerRequest<?> request) {
        try {
            // 创建一个临时的服务列表，包含所有URL
            StaticServerList tempServerList = new StaticServerList();
            
            // 执行负载均衡选择
            Server server = loadBalancer.choose(tempServerList.getServers("temp-service"));
            
            if (server != null) {
                return server.getUrl();
            }
            
            throw new IllegalStateException("No URL available for load balancing");
        } catch (Exception e) {
            logger.error("Load balancing failed", e);
            throw new RuntimeException("Failed to choose URL for load balancing", e);
        }
    }

    /**
     * 从URL数组中选择一个URL（用于定时任务负载均衡）
     *
     * @param urls URL数组
     * @return 选中的URL
     */
    public String choose(String[] urls) {
        try {
            if (urls == null || urls.length == 0) {
                throw new IllegalArgumentException("URL array cannot be null or empty");
            }
            
            // 创建临时服务器列表
            List<Server> servers = new ArrayList<>();
            for (String url : urls) {
                if (url != null && !url.trim().isEmpty()) {
                    String trimmedUrl = url.trim();
                    // 解析URL并创建Server对象
                    try {
                        String scheme = "http";
                        String host;
                        int port = 80;
                        
                        // 提取协议
                        if (trimmedUrl.contains("://")) {
                            scheme = trimmedUrl.substring(0, trimmedUrl.indexOf("://"));
                            trimmedUrl = trimmedUrl.substring(trimmedUrl.indexOf("://") + 3);
                        }
                        
                        // 提取主机和端口
                        String[] parts = trimmedUrl.split("/");
                        String hostPort = parts[0];
                        
                        if (hostPort.contains(":")) {
                            String[] hostPortParts = hostPort.split(":");
                            host = hostPortParts[0];
                            port = Integer.parseInt(hostPortParts[1]);
                        } else {
                            host = hostPort;
                            port = "https".equals(scheme) ? 443 : 80;
                        }
                        
                        servers.add(new Server(host, port));
                    } catch (Exception e) {
                        logger.warn("Failed to parse URL: {}, skipping", url, e);
                    }
                }
            }
            
            if (servers.isEmpty()) {
                throw new IllegalArgumentException("No valid URLs provided");
            }
            
            // 执行负载均衡选择
            Server server = loadBalancer.choose(servers);
            
            if (server != null) {
                return server.getUrl();
            }
            
            throw new IllegalStateException("No URL available for load balancing");
        } catch (Exception e) {
            logger.error("Load balancing failed", e);
            throw new RuntimeException("Failed to choose URL for load balancing", e);
        }
    }

    /**
     * 使用服务名执行请求
     *
     * @param serviceName  服务名
     * @param request      请求对象
     * @param responseType 响应类型
     * @return 响应实体
     */
    private <T> HttpResponse<T> execute(String serviceName, LoadBalancerRequest<T> request, Type responseType) {
        List<Server> servers = serverList.getUpServers(serviceName);
        if (servers == null || servers.isEmpty()) {
            throw new IllegalStateException("No servers available for service: " + serviceName);
        }

        // 过滤掉不健康的服务器
        List<Server> healthyServers = servers.stream()
                .filter(Server::isAlive)
                .toList();

        if (healthyServers.isEmpty()) {
            throw new IllegalStateException("No healthy servers available for service: " + serviceName);
        }

        Server server;
        for (int i = 0; i < this.loadBalancerConfig.getHealthCheckRetryCount(); i++) {
            try {
                server = loadBalancer.choose(healthyServers);
                return doExecute(serviceName,request, server);
            } catch (Throwable t) {
                logger.error("{} count request was failed", i, t);
            }
        }
        throw new IllegalStateException("No healthy servers available for service: " + serviceName);

    }

    <T> HttpResponse<T> doExecute(String serviceName,LoadBalancerRequest<T> request, Server server) {
        try {
            if (server == null) {
                throw new IllegalStateException("No available server for service: " + serviceName);
            }

            // 更新服务器访问时间
            server.setLastAccessTime(System.currentTimeMillis());
            // 增加并发请求计数
            server.incrementConcurrentRequests();

            // 直接执行请求（LoadBalancerRequest应该已经包含了完整的请求信息）
            HttpResponse<T> response = request.apply(server);

            // 记录响应时间
            server.setResponseTime(System.currentTimeMillis() - server.getLastAccessTime());

            return response;

        } catch (Exception e) {
            // 记录请求失败
            if(server != null) {
                server.recordHealthCheckFailure();
                server.updateHealthStatus(
                        loadBalancerConfig.getHealthCheckFailureThreshold(),
                        loadBalancerConfig.getHealthCheckSuccessThreshold()
                );
            }
            if (!this.healthChecker.isHealthy(server)) {
                throw new RuntimeException("Request failed for server: " + serviceName + ",ready to retry!", e);
            }
        } finally {
            if(server != null) {
                // 减少并发请求计数
                server.decrementConcurrentRequests();
            }
        }
        return HttpResponse.noContent();
    }

    /**
     * 执行HTTP GET请求
     *
     * @param serviceName  服务名
     * @param path         请求路径
     * @param responseType 响应类型
     * @return 响应实体
     */
    public <T> HttpResponse<T> get(String serviceName, String path, Type responseType) {
        return execute(serviceName, server -> {
            String url = server.getUrl() + path;
            return httpClient.get(url, responseType);
        }, responseType);
    }

    /**
     * 执行HTTP POST请求
     *
     * @param serviceName  服务名
     * @param path         请求路径
     * @param request      请求对象
     * @param responseType 响应类型
     * @return 响应实体
     */
    public <T> HttpResponse<T> post(String serviceName, String path, Object request, Type responseType) {
        return execute(serviceName, server -> {
            String url = server.getUrl() + path;
            return httpClient.post(url, request, responseType);
        }, responseType);
    }

    /**
     * 执行HTTP PUT请求
     *
     * @param serviceName  服务名
     * @param path         请求路径
     * @param request      请求对象
     * @param responseType 响应类型
     * @return 响应实体
     */
    public <T> HttpResponse<T> put(String serviceName, String path, Object request, Type responseType) {
        return execute(serviceName, server -> {
            String url = server.getUrl() + path;
            return httpClient.put(url, request, responseType);
        }, responseType);
    }

    /**
     * 执行HTTP DELETE请求
     *
     * @param serviceName  服务名
     * @param path         请求路径
     * @param responseType 响应类型
     * @return 响应实体
     */
    public <T> HttpResponse<T> delete(String serviceName, String path, Type responseType) {
        return execute(serviceName, server -> {
            String url = server.getUrl() + path;
            return httpClient.delete(url, responseType);
        }, responseType);
    }

    /**
     * 关闭客户端
     */
    public void close() {
        if (healthCheckScheduler != null) {
            healthCheckScheduler.shutdown();
            try {
                if (!healthCheckScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    healthCheckScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                healthCheckScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("负载均衡客户端已关闭");
        }
    }

    /**
     * 获取负载均衡器
     */
    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    /**
     * 获取服务器列表
     */
    public ServerList getServerList() {
        return serverList;
    }

    /**
     * 获取HTTP客户端
     */
    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * 获取健康检查器
     */
    public HealthChecker getHealthChecker() {
        return healthChecker;
    }

    /**
     * 获取负载均衡配置
     */
    public LoadBalancerConfig getLoadBalancerConfig() {
        return loadBalancerConfig;
    }
}