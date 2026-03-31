package com.cq.panel.common.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
    private final HealthCheckConfig healthCheckConfig;
    private final ScheduledExecutorService healthCheckScheduler;

    public LoadBalancerClient(LoadBalancer loadBalancer, ServerList serverList) {
        this(loadBalancer, serverList, new SimpleHttpClient(), new TcpHealthChecker(), HealthCheckConfig.defaultConfig());
    }

    public LoadBalancerClient(LoadBalancer loadBalancer, ServerList serverList, HttpClient httpClient) {
        this(loadBalancer, serverList, httpClient, new TcpHealthChecker(), HealthCheckConfig.defaultConfig());
    }

    public LoadBalancerClient(LoadBalancer loadBalancer, ServerList serverList, HealthChecker healthChecker) {
        this(loadBalancer, serverList, new SimpleHttpClient(), healthChecker, HealthCheckConfig.defaultConfig());
    }

    public LoadBalancerClient(LoadBalancer loadBalancer, ServerList serverList, HealthCheckConfig healthCheckConfig) {
        this(loadBalancer, serverList, new SimpleHttpClient(), new TcpHealthChecker(), healthCheckConfig);
    }

    public LoadBalancerClient(LoadBalancer loadBalancer, ServerList serverList, HttpClient httpClient, HealthCheckConfig healthCheckConfig) {
        this(loadBalancer, serverList, httpClient, new TcpHealthChecker(), healthCheckConfig);
    }

    public LoadBalancerClient(LoadBalancer loadBalancer, ServerList serverList, HttpClient httpClient, 
                             HealthChecker healthChecker, HealthCheckConfig healthCheckConfig) {
        this.loadBalancer = loadBalancer;
        this.serverList = serverList;
        this.httpClient = httpClient;
        this.healthChecker = healthChecker;
        this.healthCheckConfig = healthCheckConfig;
        
        // 启动健康检查调度器
        if (healthCheckConfig.isEnabled()) {
            this.healthCheckScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "health-check-scheduler");
                t.setDaemon(true);
                return t;
            });
            startHealthCheck();
        } else {
            this.healthCheckScheduler = null;
        }
    }

    /**
     * 启动健康检查
     */
    private void startHealthCheck() {
        if (healthCheckConfig.isEnabled() && healthCheckScheduler != null) {
            healthCheckScheduler.scheduleAtFixedRate(this::performHealthCheck, 
                    0, healthCheckConfig.getIntervalMs(), TimeUnit.MILLISECONDS);
            logger.info("健康检查已启动，间隔: {}ms", healthCheckConfig.getIntervalMs());
        }
    }

    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
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
                    if (server.needsHealthCheck(healthCheckConfig.getIntervalMs())) {
                        boolean healthy = healthChecker.isHealthy(server);
                        
                        if (healthy) {
                            server.recordHealthCheckSuccess();
                        } else {
                            server.recordHealthCheckFailure();
                        }
                        
                        // 更新服务器状态
                        server.updateHealthStatus(
                            healthCheckConfig.getFailureThreshold(), 
                            healthCheckConfig.getSuccessThreshold()
                        );
                        
                        logger.debug("健康检查完成: {} - {}", server.getId(), healthy ? "健康" : "不健康");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("健康检查异常", e);
        }
    }

    /**
     * 使用服务名执行请求
     * 
     * @param serviceName 服务名
     * @param request 请求对象
     * @param responseType 响应类型
     * @return 响应实体
     */
    public <T> HttpResponse<T> execute(String serviceName, LoadBalancerRequest<T> request, Class<T> responseType) {
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

        Server server = loadBalancer.choose(healthyServers);
        if (server == null) {
            throw new IllegalStateException("No available server for service: " + serviceName);
        }

        // 更新服务器访问时间
        server.setLastAccessTime(System.currentTimeMillis());
        
        try {
            // 增加并发请求计数
            server.incrementConcurrentRequests();
            
            // 直接执行请求（LoadBalancerRequest应该已经包含了完整的请求信息）
            HttpResponse<T> response = request.apply(server);
            
            // 记录响应时间
            server.setResponseTime(System.currentTimeMillis() - server.getLastAccessTime());
            
            return response;
            
        } catch (Exception e) {
            // 记录请求失败
            server.recordHealthCheckFailure();
            server.updateHealthStatus(
                healthCheckConfig.getFailureThreshold(), 
                healthCheckConfig.getSuccessThreshold()
            );
            throw new RuntimeException("Request failed for server: " + server.getId(), e);
        } finally {
            // 减少并发请求计数
            server.decrementConcurrentRequests();
        }
    }

    /**
     * 执行HTTP GET请求
     * 
     * @param serviceName 服务名
     * @param path 请求路径
     * @param responseType 响应类型
     * @return 响应实体
     */
    public <T> HttpResponse<T> get(String serviceName, String path, Class<T> responseType) {
        return execute(serviceName, server -> {
            String url = server.getUrl() + path;
            return httpClient.get(url, responseType);
        }, responseType);
    }

    /**
     * 执行HTTP POST请求
     * 
     * @param serviceName 服务名
     * @param path 请求路径
     * @param request 请求对象
     * @param responseType 响应类型
     * @return 响应实体
     */
    public <T> HttpResponse<T> post(String serviceName, String path, Object request, Class<T> responseType) {
        return execute(serviceName, server -> {
            String url = server.getUrl() + path;
            return httpClient.post(url, request, responseType);
        }, responseType);
    }

    /**
     * 执行HTTP PUT请求
     * 
     * @param serviceName 服务名
     * @param path 请求路径
     * @param request 请求对象
     * @param responseType 响应类型
     * @return 响应实体
     */
    public <T> HttpResponse<T> put(String serviceName, String path, Object request, Class<T> responseType) {
        return execute(serviceName, server -> {
            String url = server.getUrl() + path;
            return httpClient.put(url, request, responseType);
        }, responseType);
    }

    /**
     * 执行HTTP DELETE请求
     * 
     * @param serviceName 服务名
     * @param path 请求路径
     * @param responseType 响应类型
     * @return 响应实体
     */
    public <T> HttpResponse<T> delete(String serviceName, String path, Class<T> responseType) {
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
     * 获取健康检查配置
     */
    public HealthCheckConfig getHealthCheckConfig() {
        return healthCheckConfig;
    }
}