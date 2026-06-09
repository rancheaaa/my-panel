package com.cq.panel.admin.server.service.proxy;

import com.cq.panel.common.loadbalancer.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Proxy健康检查器
 * 通过调用Proxy的 /forward/ping 端点检测存活状态
 */
@Component
public class ProxyHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(ProxyHealthChecker.class);
    private static final String PING_PATH = "/forward/ping";

    private final RestTemplate restTemplate;

    public ProxyHealthChecker(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 检查单个Proxy Server是否存活
     *
     * @param server Proxy服务器
     * @return true=存活
     */
    public boolean isHealthy(Server server) {
        if (server == null) {
            return false;
        }
        try {
            String url = server.getUrl() + PING_PATH;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            boolean healthy = response.getStatusCode().is2xxSuccessful();
            if (!healthy) {
                log.debug("Proxy健康检查失败: {} - status={}", server.getUrl(), response.getStatusCode());
            }
            return healthy;
        } catch (Exception e) {
            log.debug("Proxy健康检查异常: {} - {}", server.getUrl(), e.getMessage());
            return false;
        }
    }

    /**
     * 批量检查所有Proxy Server
     * 根据连续成功/失败阈值更新Server存活状态
     *
     * @param serverList       服务器列表
     * @param failureThreshold 连续失败N次标记宕机
     * @param successThreshold 连续成功N次标记恢复
     */
    public void checkAll(ProxyServerList serverList, int failureThreshold, int successThreshold) {
        List<Server> servers = serverList.getServers();
        for (Server server : servers) {
            boolean healthy = isHealthy(server);
            if (healthy) {
                server.recordHealthCheckSuccess();
            } else {
                server.recordHealthCheckFailure();
            }
            // 根据阈值更新状态
            boolean wasAlive = server.isAlive();
            server.updateHealthStatus(failureThreshold, successThreshold);
            boolean isAlive = server.isAlive();

            if (wasAlive && !isAlive) {
                serverList.markDown(server);
            } else if (!wasAlive && isAlive) {
                serverList.markAlive(server);
            }
        }
    }
}
