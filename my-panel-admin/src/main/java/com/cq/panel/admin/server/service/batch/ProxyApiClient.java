package com.cq.panel.admin.server.service.batch;

import com.cq.panel.admin.server.repository.domain.RcNode;
import com.cq.panel.admin.server.repository.service.IRcNodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

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

    private ProxyApi createProxyApi() {
        String baseUrl = resolveProxyBaseUrl();
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(ProxyApi.class);
    }

    public Map<String, Object> startTask(Long taskId, Map<String, Object> request) {
        return createProxyApi().startTask(taskId, request);
    }

    public Map<String, Object> pauseTask(Long taskId, String sourceAgentId, String sourceAgentApiUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceAgentId", sourceAgentId);
        body.put("sourceAgentApiUrl", sourceAgentApiUrl);
        return createProxyApi().pauseTask(taskId, body);
    }

    public Map<String, Object> resumeTask(Long taskId, String sourceAgentId, String sourceAgentApiUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceAgentId", sourceAgentId);
        body.put("sourceAgentApiUrl", sourceAgentApiUrl);
        return createProxyApi().resumeTask(taskId, body);
    }

    public Map<String, Object> cancelTask(Long taskId, String sourceAgentId, String sourceAgentApiUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceAgentId", sourceAgentId);
        body.put("sourceAgentApiUrl", sourceAgentApiUrl);
        return createProxyApi().cancelTask(taskId, body);
    }
}
