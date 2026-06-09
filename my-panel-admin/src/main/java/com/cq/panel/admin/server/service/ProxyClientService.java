package com.cq.panel.admin.server.service;

import com.cq.panel.admin.server.web.domain.dto.proxy.AgentResponse;
import com.cq.panel.admin.server.web.domain.dto.proxy.PingResult;
import com.cq.panel.admin.server.web.domain.dto.proxy.ProxyApiResponse;
import com.cq.panel.admin.server.web.domain.dto.proxy.TcpProbeResultData;
import com.fasterxml.jackson.core.type.TypeReference;
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
     * @return Proxy Ping响应
     */
    public ProxyApiResponse<PingResult> ping() {
        String body = getForString(proxyUrl + "/forward/ping");
        return parseResponse(body, new TypeReference<ProxyApiResponse<PingResult>>() {});
    }

    /**
     * 转发命令执行请求到Agent（通过Proxy）
     *
     * @param agentId     Agent节点ID
     * @param requestBody Agent原始请求体JSON (command, timeout)
     * @return Proxy响应，data字段为Agent原始响应JSON字符串
     */
    public ProxyApiResponse<String> executeCommand(String agentId, String requestBody) {
        String url = buildUrl("/forward/agent/execute", Map.of("agentId", agentId));
        return postForTypedResponse(url, requestBody);
    }

    /**
     * 转发连通性探测请求到Agent（通过Proxy）
     *
     * @param agentId   源Agent节点ID
     * @param targetIp  目标IP
     * @param targetPort 目标端口
     * @return Proxy响应，data字段为Agent原始响应JSON字符串
     */
    public ProxyApiResponse<String> probeViaAgent(String agentId, String targetIp, int targetPort) {
        String url = buildUrl("/forward/agent/probe", Map.of(
                "agentId", agentId,
                "host", targetIp,
                "port", String.valueOf(targetPort)));
        return parseResponse(getForString(url), stringResponseType());
    }

    /**
     * 从Proxy发起TCP端口探测
     *
     * @param host 目标IP
     * @param port 目标端口
     * @return Proxy响应，data为TcpProbeResultData
     */
    public ProxyApiResponse<TcpProbeResultData> tcpProbe(String host, int port) {
        String url = buildUrl("/forward/agent/tcp-probe", Map.of(
                "host", host,
                "port", String.valueOf(port)));
        return parseResponse(getForString(url), tcpProbeResponseType());
    }

    /**
     * 转发目录详情检查请求到Agent（通过Proxy）
     *
     * @param agentId Agent节点ID
     * @param path    目录路径
     * @return Proxy响应，data字段为Agent原始响应JSON字符串
     */
    public ProxyApiResponse<String> dirCheck(String agentId, String path) {
        String url = buildUrl("/forward/agent/file/dir-check", Map.of(
                "agentId", agentId,
                "path", path));
        return parseResponse(getForString(url), stringResponseType());
    }

    /**
     * 转发目录存在性检查请求到Agent（通过Proxy）
     *
     * @param agentId Agent节点ID
     * @param path    目录路径
     * @return Proxy响应，data字段为Agent原始响应JSON字符串
     */
    public ProxyApiResponse<String> fileExists(String agentId, String path) {
        String url = buildUrl("/forward/agent/file/exists", Map.of(
                "agentId", agentId,
                "path", path));
        return parseResponse(getForString(url), stringResponseType());
    }

    /**
     * 转发配置校验请求到Agent（通过Proxy）
     *
     * @param agentId Agent节点ID
     * @return Proxy响应，data字段为Agent原始响应JSON字符串
     */
    public ProxyApiResponse<String> configVerify(String agentId) {
        String url = buildUrl("/forward/agent/config/verify", Map.of("agentId", agentId));
        return parseResponse(getForString(url), stringResponseType());
    }

    /**
     * 转发配置推送初始化请求到Agent（通过Proxy）
     *
     * @param agentId     Agent节点ID
     * @param requestBody Agent原始请求体JSON (totalSize, totalChunks, chunkSize)
     * @return Proxy响应，data字段为Agent原始响应JSON字符串
     */
    public ProxyApiResponse<String> configPushInit(String agentId, String requestBody) {
        String url = buildUrl("/forward/agent/config/push/init", Map.of("agentId", agentId));
        return postForTypedResponse(url, requestBody);
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
     * @return Proxy响应，data字段为Agent原始响应JSON字符串
     */
    public ProxyApiResponse<String> configPushComplete(String agentId, String requestBody) {
        String url = buildUrl("/forward/agent/config/push/complete", Map.of("agentId", agentId));
        return postForTypedResponse(url, requestBody);
    }

    // ==================== 通用解析方法 ====================

    /**
     * 解析Proxy API响应为类型化对象
     *
     * @param responseBody   原始响应体JSON字符串
     * @param responseType   data字段的TypeReference
     * @param <T>            data字段的泛型类型
     * @return 类型化的ProxyApiResponse，解析失败返回code非200的对象
     */
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

    /**
     * 解析Agent原始响应JSON字符串为类型化对象
     * 用于从Proxy转发的响应中提取Agent的实际数据
     *
     * @param agentResponseJson Agent原始响应JSON字符串
     * @param dataType          data字段的TypeReference
     * @param <T>               data字段的泛型类型
     * @return Agent响应对象，解析失败返回success=false的对象
     */
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

    private ProxyApiResponse<String> postForTypedResponse(String url, String body) {
        String responseBody = postForString(url, body);
        return parseResponse(responseBody, stringResponseType());
    }

    // 预定义的TypeReference实例，避免重复创建
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
