package com.cq.panel.admin.server.service.batch;

import com.cq.panel.admin.server.repository.domain.RcNode;
import com.cq.panel.admin.server.repository.service.IRcNodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ProxyApiClient {
    private static final Logger logger = LoggerFactory.getLogger(ProxyApiClient.class);

    private final IRcNodeService rcNodeService;
    private final AtomicInteger nodeIndex = new AtomicInteger(0);

    @Value("${app.proxy.base-url:}")
    private String configuredProxyBaseUrl;

    public ProxyApiClient(IRcNodeService rcNodeService) {
        this.rcNodeService = rcNodeService;
    }

    private String resolveProxyBaseUrl() {
        try {
            RcNode query = new RcNode();
            query.setStatus("0");
            List<RcNode> onlineNodes = rcNodeService.selectRcNodeList(query);
            if (onlineNodes != null && !onlineNodes.isEmpty()) {
                int idx = Math.abs(nodeIndex.getAndIncrement() % onlineNodes.size());
                RcNode node = onlineNodes.get(idx);
                String url = "http://" + node.getNodeIp() + ":" + node.getNodePort();
                logger.info("Resolved proxy base URL from rc_node: {} (nodeId={})", url, node.getId());
                return url;
            }
        } catch (Exception e) {
            logger.warn("Failed to resolve proxy address from rc_node table: {}", e.getMessage());
        }

        if (configuredProxyBaseUrl != null && !configuredProxyBaseUrl.isEmpty()) {
            logger.info("Using configured proxy base URL: {}", configuredProxyBaseUrl);
            return configuredProxyBaseUrl;
        }

        logger.warn(
                "No online proxy nodes found in rc_node and no fallback configured, using default: http://localhost:9876");
        return "http://localhost:9876";
    }

    private RestClient createRestClient() {
        String baseUrl = resolveProxyBaseUrl();
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public Map<String, Object> startTask(Long taskId, Map<String, Object> request) {
        logger.info("Sending start task request to proxy for task {}", taskId);
        try {
            return createRestClient()
                    .post()
                    .uri("/api/v1/batch/tasks/{taskId}/start", taskId)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            logger.error("Failed to start task {} via proxy: {}", taskId, e.getMessage(), e);
            throw new RuntimeException("Proxy启动任务失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> pauseTask(Long taskId, String sourceAgentId, String sourceAgentApiUrl) {
        logger.info("Sending pause task request to proxy for task {}", taskId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceAgentId", sourceAgentId);
        body.put("sourceAgentApiUrl", sourceAgentApiUrl);
        try {
            return createRestClient()
                    .put()
                    .uri("/api/v1/batch/tasks/{taskId}/pause", taskId)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            logger.error("Failed to pause task {} via proxy: {}", taskId, e.getMessage(), e);
            throw new RuntimeException("Proxy暂停任务失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> resumeTask(Long taskId, String sourceAgentId, String sourceAgentApiUrl) {
        logger.info("Sending resume task request to proxy for task {}", taskId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceAgentId", sourceAgentId);
        body.put("sourceAgentApiUrl", sourceAgentApiUrl);
        try {
            return createRestClient()
                    .put()
                    .uri("/api/v1/batch/tasks/{taskId}/resume", taskId)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            logger.error("Failed to resume task {} via proxy: {}", taskId, e.getMessage(), e);
            throw new RuntimeException("Proxy恢复任务失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> cancelTask(Long taskId, String sourceAgentId, String sourceAgentApiUrl) {
        logger.info("Sending cancel task request to proxy for task {}", taskId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceAgentId", sourceAgentId);
        body.put("sourceAgentApiUrl", sourceAgentApiUrl);
        try {
            return createRestClient()
                    .put()
                    .uri("/api/v1/batch/tasks/{taskId}/cancel", taskId)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            logger.error("Failed to cancel task {} via proxy: {}", taskId, e.getMessage(), e);
            throw new RuntimeException("Proxy取消任务失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> updateTaskConfig(Long taskId, Map<String, Object> request) {
        logger.info("Sending update config request to proxy for task {}", taskId);
        try {
            return createRestClient()
                    .put()
                    .uri("/api/v1/batch/tasks/{taskId}/config", taskId)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            logger.error("Failed to update config for task {} via proxy: {}", taskId, e.getMessage(), e);
            throw new RuntimeException("Proxy更新配置失败: " + e.getMessage(), e);
        }
    }
}
