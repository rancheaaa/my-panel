package com.cq.panel.admin.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Proxy客户端服务
 * Admin通过此服务调用Proxy的转发端点，实现Admin->Proxy->Agent的调用链路
 */
@Service
public class ProxyClientService {

    private static final Logger log = LoggerFactory.getLogger(ProxyClientService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String proxyUrl;

    public ProxyClientService(RestTemplate restTemplate,
                               ObjectMapper objectMapper,
                               @Value("${app.proxy-url:http://localhost:9876}") String proxyUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.proxyUrl = proxyUrl;
    }

    /**
     * 检测Admin到Proxy的连通性（Ping）
     *
     * @return Proxy响应体JSON字符串，如果连接失败抛异常
     */
    public String ping() {
        return getForString(proxyUrl + "/forward/ping");
    }

    /**
     * 转发命令执行请求到Agent（通过Proxy）
     *
     * @param agentId     Agent节点ID
     * @param requestBody Agent原始请求体JSON (command, timeout)
     * @return Agent响应体JSON字符串
     */
    public String executeCommand(String agentId, String requestBody) {
        String url = buildUrl("/forward/agent/execute", Map.of("agentId", agentId));
        return postForString(url, requestBody);
    }

    /**
     * 转发连通性探测请求到Agent（通过Proxy）
     *
     * @param agentId   源Agent节点ID
     * @param targetIp  目标IP
     * @param targetPort 目标端口
     * @return Agent响应体JSON字符串
     */
    public String probeViaAgent(String agentId, String targetIp, int targetPort) {
        String url = buildUrl("/forward/agent/probe", Map.of(
                "agentId", agentId,
                "host", targetIp,
                "port", String.valueOf(targetPort)));
        return getForString(url);
    }

    /**
     * 从Proxy发起TCP端口探测
     *
     * @param host 目标IP
     * @param port 目标端口
     * @return 探测结果JSON字符串
     */
    public String tcpProbe(String host, int port) {
        String url = buildUrl("/forward/agent/tcp-probe", Map.of(
                "host", host,
                "port", String.valueOf(port)));
        return getForString(url);
    }

    /**
     * 转发目录详情检查请求到Agent（通过Proxy）
     *
     * @param agentId Agent节点ID
     * @param path    目录路径
     * @return Agent响应体JSON字符串
     */
    public String dirCheck(String agentId, String path) {
        String url = buildUrl("/forward/agent/file/dir-check", Map.of(
                "agentId", agentId,
                "path", path));
        return getForString(url);
    }

    /**
     * 转发目录存在性检查请求到Agent（通过Proxy）
     *
     * @param agentId Agent节点ID
     * @param path    目录路径
     * @return Agent响应体JSON字符串
     */
    public String fileExists(String agentId, String path) {
        String url = buildUrl("/forward/agent/file/exists", Map.of(
                "agentId", agentId,
                "path", path));
        return getForString(url);
    }

    /**
     * 转发配置校验请求到Agent（通过Proxy）
     *
     * @param agentId Agent节点ID
     * @return Agent响应体JSON字符串
     */
    public String configVerify(String agentId) {
        String url = buildUrl("/forward/agent/config/verify", Map.of("agentId", agentId));
        return getForString(url);
    }

    /**
     * 转发配置推送初始化请求到Agent（通过Proxy）
     *
     * @param agentId     Agent节点ID
     * @param requestBody Agent原始请求体JSON (totalSize, totalChunks, chunkSize)
     * @return Agent响应体JSON字符串
     */
    public String configPushInit(String agentId, String requestBody) {
        String url = buildUrl("/forward/agent/config/push/init", Map.of("agentId", agentId));
        return postForString(url, requestBody);
    }

    /**
     * 转发配置推送分块请求到Agent（通过Proxy）
     *
     * @param agentId     Agent节点ID
     * @param requestBody Agent原始请求体JSON (sessionId, chunkIndex, data)
     */
    public void configPushChunk(String agentId, String requestBody) {
        String url = buildUrl("/forward/agent/config/push/chunk", Map.of("agentId", agentId));
        postForString(url, requestBody);
    }

    /**
     * 转发配置推送完成请求到Agent（通过Proxy）
     *
     * @param agentId     Agent节点ID
     * @param requestBody Agent原始请求体JSON (sessionId)
     * @return Agent响应体JSON字符串
     */
    public String configPushComplete(String agentId, String requestBody) {
        String url = buildUrl("/forward/agent/config/push/complete", Map.of("agentId", agentId));
        return postForString(url, requestBody);
    }

    /**
     * 解析Proxy响应，提取data字段
     *
     * @param responseBody Proxy响应体
     * @return data字段的JsonNode，如果响应异常则返回null
     */
    public JsonNode extractData(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            int code = root.path("code").asInt(-1);
            if (code != 200) {
                String msg = root.path("msg").asText("未知错误");
                log.warn("Proxy返回错误: code={}, msg={}", code, msg);
                return null;
            }
            return root.path("data");
        } catch (Exception e) {
            log.warn("解析Proxy响应失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 内部方法 ====================

    private String buildUrl(String path, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(proxyUrl).path(path);
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
}
