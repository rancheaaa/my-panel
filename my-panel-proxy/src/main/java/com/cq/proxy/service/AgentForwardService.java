package com.cq.proxy.service;

import com.cq.proxy.service.batch.AgentPushService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

/**
 * Agent请求转发服务
 * 将Admin的请求转发到Agent，实现Admin->Proxy->Agent的调用链路
 */
@Service
public class AgentForwardService {

    private static final Logger log = LoggerFactory.getLogger(AgentForwardService.class);
    private static final int TCP_PROBE_TIMEOUT_MS = 5000;

    private final AgentPushService agentPushService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AgentForwardService(AgentPushService agentPushService, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.agentPushService = agentPushService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 转发命令执行请求到Agent
     */
    public String forwardExecute(String agentId, String requestBody) {
        String url = resolveAgentUrl(agentId, "/api/execute");
        return forwardPost(url, requestBody, agentId, "execute");
    }

    /**
     * 转发连通性探测请求到Agent
     */
    public String forwardProbe(String agentId, String host, int port) {
        String url = resolveAgentUrl(agentId, "/api/probe?host=" + host + "&port=" + port);
        return forwardGet(url, agentId, "probe");
    }

    /**
     * 从Proxy发起TCP端口探测
     */
    public Map<String, Object> tcpProbe(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TCP_PROBE_TIMEOUT_MS);
            return Map.of("reachable", true, "host", host, "port", port);
        } catch (Exception e) {
            String reason = classifyConnectionFailure(e);
            log.warn("TCP探测失败 {}:{} - {}", host, port, reason);
            return Map.of("reachable", false, "host", host, "port", port, "failureReason", reason);
        }
    }

    /**
     * 转发目录详情检查请求到Agent
     */
    public String forwardDirCheck(String agentId, String path) {
        String url = resolveAgentUrl(agentId, "/api/file/dir-check?path=" + path);
        return forwardGet(url, agentId, "dir-check");
    }

    /**
     * 转发目录存在性检查请求到Agent
     */
    public String forwardFileExists(String agentId, String path) {
        String url = resolveAgentUrl(agentId, "/api/file/exists?path=" + path);
        return forwardGet(url, agentId, "file-exists");
    }

    /**
     * 转发配置校验请求到Agent
     */
    public String forwardConfigVerify(String agentId) {
        String url = resolveAgentUrl(agentId, "/api/config/verify");
        return forwardGet(url, agentId, "config-verify");
    }

    /**
     * 转发配置推送初始化请求到Agent
     */
    public String forwardConfigPushInit(String agentId, String requestBody) {
        String url = resolveAgentUrl(agentId, "/api/config/push/init");
        return forwardPost(url, requestBody, agentId, "config-push-init");
    }

    /**
     * 转发配置推送分块请求到Agent
     */
    public String forwardConfigPushChunk(String agentId, String requestBody) {
        String url = resolveAgentUrl(agentId, "/api/config/push/chunk");
        return forwardPost(url, requestBody, agentId, "config-push-chunk");
    }

    /**
     * 转发配置推送完成请求到Agent
     */
    public String forwardConfigPushComplete(String agentId, String requestBody) {
        String url = resolveAgentUrl(agentId, "/api/config/push/complete");
        return forwardPost(url, requestBody, agentId, "config-push-complete");
    }

    // ==================== 内部方法 ====================

    private String resolveAgentUrl(String agentId, String endpoint) {
        AgentPushService.AgentAddress address = agentPushService.getAgentAddress(agentId);
        if (address == null) {
            throw new AgentPushService.AgentNotFoundException("Agent not found or offline: " + agentId);
        }
        return agentPushService.buildAgentUrl(address, endpoint);
    }

    private String forwardGet(String url, String agentId, String operation) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            log.debug("转发GET请求成功: agentId={}, op={}", agentId, operation);
            return response.getBody();
        } catch (Exception e) {
            log.error("转发GET请求失败: agentId={}, op={}, error={}", agentId, operation, e.getMessage());
            throw new RuntimeException("Forward GET failed [" + operation + "]: " + e.getMessage(), e);
        }
    }

    private String forwardPost(String url, String requestBody, String agentId, String operation) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.debug("转发POST请求成功: agentId={}, op={}", agentId, operation);
            return response.getBody();
        } catch (Exception e) {
            log.error("转发POST请求失败: agentId={}, op={}, error={}", agentId, operation, e.getMessage());
            throw new RuntimeException("Forward POST failed [" + operation + "]: " + e.getMessage(), e);
        }
    }

    private String classifyConnectionFailure(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "未知原因";
        String lowerMsg = msg.toLowerCase();
        if (lowerMsg.contains("connection refused")) return "连接被拒绝（目标进程未启动或端口未监听）";
        if (lowerMsg.contains("network is unreachable") || lowerMsg.contains("no route to host")) return "网络不可达（可能因网络策略限制）";
        if (lowerMsg.contains("connection timed out") || lowerMsg.contains("timed out")) return "连接超时（可能因防火墙拦截或网络不通）";
        if (lowerMsg.contains("connection reset")) return "连接被重置（对端拒绝连接）";
        if (lowerMsg.contains("unreachable")) return "目标主机不可达";
        if (lowerMsg.contains("permission denied")) return "权限被拒绝（可能因安全策略限制）";
        return "未知原因: " + msg;
    }
}
