package com.cq.panel.admin.server.service.proxy;

import com.cq.panel.common.loadbalancer.Server;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Proxy负载均衡器
 * 负责Server选择、故障转移、定时健康检查
 */
@Component
public class ProxyLoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(ProxyLoadBalancer.class);

    /**
     * -- GETTER --
     *  获取服务器列表
     */
    @Getter
    private final ProxyServerList serverList;
    private final ProxyHealthChecker healthChecker;
    private final ProxyLoadBalancerConfig config;
    private final ScheduledExecutorService healthCheckScheduler;

    public ProxyLoadBalancer(ProxyServerList serverList,
                             ProxyHealthChecker healthChecker,
                             ProxyLoadBalancerConfig config) {
        this.serverList = serverList;
        this.healthChecker = healthChecker;
        this.config = config;
        this.healthCheckScheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "proxy-health-check");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 选择一个可用的Proxy Server
     * 优先选择存活Server轮询；全部宕机时降级轮询所有（给恢复机会）
     *
     * @return 选中的Server，空列表返回null
     */
    public Server chooseServer() {
        return serverList.next();
    }

    /**
     * 带故障转移的请求执行
     * 请求失败时自动标记Server并切换到下一个重试
     *
     * @param request 负载均衡请求
     * @param <T>     响应类型
     * @return 请求结果
     * @throws IllegalStateException 所有Server均不可用时抛出
     */
    public <T> T executeWithFailover(LoadBalancedRequest<T> request) {
        if (serverList.isEmpty()) {
            throw new IllegalStateException("没有可用的Proxy Server，请检查app.proxy-urls配置");
        }

        int maxAttempts = Math.min(config.getRetryCount() + 1, serverList.size());
        Exception lastException = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            Server server = chooseServer();
            if (server == null) {
                throw new IllegalStateException("没有可用的Proxy Server");
            }
            try {
                // 请求成功，标记Server存活（如果是降级请求成功，有助于恢复）
                return request.execute(server);
            } catch (Exception e) {
                lastException = e;
                log.warn("Proxy请求失败(第{}次): server={} - {}", attempt + 1, server.getUrl(), e.getMessage());
                // 标记Server为宕机
                serverList.markDown(server);
            }
        }

        // 所有重试都失败，抛出异常
        throw new IllegalStateException(
                "所有Proxy Server均不可用，已尝试" + maxAttempts + "次", lastException);
    }

    /**
     * 启动定时健康检查
     */
    @PostConstruct
    public void startHealthCheck() {
        if (!config.isHealthCheckEnabled() || serverList.isEmpty()) {
            log.info("Proxy健康检查未启用或无Server配置");
            return;
        }

        // 首次立即执行一次
        performHealthCheck();

        // 定时执行
        healthCheckScheduler.scheduleAtFixedRate(
                this::performHealthCheck,
                config.getHealthCheckInterval(),
                config.getHealthCheckInterval(),
                TimeUnit.MILLISECONDS
        );
        log.info("Proxy健康检查已启动，间隔: {}ms，Server数: {}", config.getHealthCheckInterval(), serverList.size());
    }

    /**
     * 执行一次健康检查
     */
    public void performHealthCheck() {
        try {
            healthChecker.checkAll(serverList,
                    config.getHealthCheckFailureThreshold(),
                    config.getHealthCheckSuccessThreshold());
            log.debug("Proxy健康检查完成 - 存活: {}, 宕机: {}",
                    serverList.getAliveServers().size(),
                    serverList.getDownServers().size());
        } catch (Exception e) {
            log.error("Proxy健康检查异常", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        healthCheckScheduler.shutdown();
        try {
            if (!healthCheckScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                healthCheckScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthCheckScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Proxy负载均衡器已关闭");
    }
}
