package com.cq.panel.admin.server.service;

import com.cq.panel.admin.server.service.proxy.ProxyLoadBalancer;
import com.cq.panel.admin.server.web.domain.dto.proxy.AgentResponse;
import com.cq.panel.admin.server.web.domain.dto.proxy.PingResult;
import com.cq.panel.admin.server.web.domain.dto.proxy.ProxyApiResponse;
import com.cq.panel.admin.server.web.domain.dto.proxy.TcpProbeResultData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.Map;

/**
 * Proxy客户端服务
 * Admin通过此服务调用Proxy的转发端点，实现Admin->Proxy->Agent的调用链路
 * 支持多Proxy地址负载均衡和故障转移
 */
@Service
public class ProxyClientService {

    private static final Logger log = LoggerFactory.getLogger(ProxyClientService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ProxyLoadBalancer loadBalancer;

    public ProxyClientService(RestTemplate restTemplate,
                               ObjectMapper objectMapper,
                               ProxyLoadBalancer loadBalancer) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.loadBalancer = loadBalancer;
    }

    /**
     * 检测Admin到Proxy的连通性（Ping）
     */
    public ProxyApiResponse<PingResult> ping() {
        return loadBalancer.executeWithFailover(server -> {
            String body = getForString(server.getUrl() + "/forward/ping");
            return parseResponse(body, new TypeReference<>() {
            });
        });
    }

    /**
     * 转发命令执行请求到Agent（通过Proxy）
     */
    public ProxyApiResponse<String> executeCommand(String agentId, String requestBody) {
        return executeWithFailover("/forward/agent/execute", Map.of("agentId", agentId), requestBody);
    }

    /**
     * 转发连通性探测请求到Agent（通过Proxy）
     */
    public ProxyApiResponse<String> probeViaAgent(String agentId, String targetIp, int targetPort) {
        String path = "/forward/agent/probe";
        Map<String, String> params = Map.of("agentId", agentId, "host", targetIp, "port", String.valueOf(targetPort));
        return loadBalancer.executeWithFailover(server -> {
            String url = buildUrl(server.getUrl(), path, params);
            return parseResponse(getForString(url), stringResponseType());
        });
    }

    /**
     * 从Proxy发起TCP端口探测
     */
    public ProxyApiResponse<TcpProbeResultData> tcpProbe(String host, int port) {
        return loadBalancer.executeWithFailover(server -> {
            String url = buildUrl(server.getUrl(), "/forward/agent/tcp-probe",
                    Map.of("host", host, "port", String.valueOf(port)));
            return parseResponse(getForString(url), tcpProbeResponseType());
        });
    }

    /**
     * 转发目录详情检查请求到Agent（通过Proxy）
     */
    public ProxyApiResponse<String> dirCheck(String agentId, String path) {
        return executeGetWithFailover("/forward/agent/file/dir-check",
                Map.of("agentId", agentId, "path", path));
    }

    /**
     * 转发目录存在性检查请求到Agent（通过Proxy）
     */
    public ProxyApiResponse<String> fileExists(String agentId, String path) {
        return executeGetWithFailover("/forward/agent/file/exists",
                Map.of("agentId", agentId, "path", path));
    }

    /**
     * 转发配置校验请求到Agent（通过Proxy）
     */
    public ProxyApiResponse<String> configVerify(String agentId) {
        return executeGetWithFailover("/forward/agent/config/verify",
                Map.of("agentId", agentId));
    }

    /**
     * 转发配置推送初始化请求到Agent（通过Proxy）
     */
    public ProxyApiResponse<String> configPushInit(String agentId, String requestBody) {
        return executeWithFailover("/forward/agent/config/push/init",
                Map.of("agentId", agentId), requestBody);
    }

    /**
     * 转发配置推送分块请求到Agent（通过Proxy）
     */
    public void configPushChunk(String agentId, String requestBody) {
        loadBalancer.executeWithFailover(server -> {
            String url = buildUrl(server.getUrl(), "/forward/agent/config/push/chunk",
                    Map.of("agentId", agentId));
            postForString(url, requestBody);
            return null;
        });
    }

    /**
     * 转发配置推送完成请求到Agent（通过Proxy）
     */
    public ProxyApiResponse<String> configPushComplete(String agentId, String requestBody) {
        return executeWithFailover("/forward/agent/config/push/complete",
                Map.of("agentId", agentId), requestBody);
    }

    // ==================== 通用解析方法 ====================

    public <T> ProxyApiResponse<T> parseResponse(String responseBody, TypeReference<ProxyApiResponse<T>> responseType) {
        try {
            ProxyApiResponse<T> response = objectMapper.readValue(responseBody, responseType);
            if (!response.isSuccess()) {
                log.warn("Proxy返回错误: code={}, msg={}", response.getCode(), response.getMsg());
            }
            return response;
        } catch (Exception e) {
            log.warn("解析Proxy响应失败: {}", e.getMessage());
            ProxyApiResponse<T> errorResponse = new ProxyApiResponse<>();
            errorResponse.setCode(-1);
            errorResponse.setMsg("Parse error: " + e.getMessage());
            return errorResponse;
        }
    }

    public <T> AgentResponse<T> parseAgentResponse(String agentResponseJson, TypeReference<AgentResponse<T>> dataType) {
        try {
            return objectMapper.readValue(agentResponseJson, dataType);
        } catch (Exception e) {
            log.warn("解析Agent响应失败: {}", e.getMessage());
            AgentResponse<T> errorResponse = new AgentResponse<>();
            errorResponse.setSuccess(false);
            errorResponse.setMsg("Parse error: " + e.getMessage());
            return errorResponse;
        }
    }

    // ==================== 内部方法 ====================

    private ProxyApiResponse<String> executeGetWithFailover(String path, Map<String, String> params) {
        return loadBalancer.executeWithFailover(server -> {
            String url = buildUrl(server.getUrl(), path, params);
            return parseResponse(getForString(url), stringResponseType());
        });
    }

    private ProxyApiResponse<String> executeWithFailover(String path, Map<String, String> params, String requestBody) {
        return loadBalancer.executeWithFailover(server -> {
            String url = buildUrl(server.getUrl(), path, params);
            String responseBody = postForString(url, requestBody);
            return parseResponse(responseBody, stringResponseType());
        });
    }

    private String buildUrl(String baseUrl, String path, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl).path(path);
        params.forEach(builder::queryParam);
        return builder.build().toUriString();
    }

    private String getForString(String url) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Proxy GET请求失败: url={}, error={}", url, e.getMessage());
            throw new RuntimeException("Proxy GET request failed: " + e.getMessage(), e);
        }
    }

    private String postForString(String url, String body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Proxy POST请求失败: url={}, error={}", url, e.getMessage());
            throw new RuntimeException("Proxy POST request failed: " + e.getMessage(), e);
        }
    }

    // 预定义的TypeReference实例
    private static TypeReference<ProxyApiResponse<String>> STRING_RESPONSE_TYPE;
    private static TypeReference<ProxyApiResponse<TcpProbeResultData>> TCP_PROBE_RESPONSE_TYPE;

    private static synchronized TypeReference<ProxyApiResponse<String>> stringResponseType() {
        if (STRING_RESPONSE_TYPE == null) {
            STRING_RESPONSE_TYPE = new TypeReference<>() {};
        }
        return STRING_RESPONSE_TYPE;
    }

    private static synchronized TypeReference<ProxyApiResponse<TcpProbeResultData>> tcpProbeResponseType() {
        if (TCP_PROBE_RESPONSE_TYPE == null) {
            TCP_PROBE_RESPONSE_TYPE = new TypeReference<>() {};
        }
        return TCP_PROBE_RESPONSE_TYPE;
    }
}
