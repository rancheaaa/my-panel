package com.cq.proxy.service.batch;

import com.cq.panel.common.loadbalancer.HttpResponse;
import com.cq.panel.common.loadbalancer.SimpleHttpClient;
import com.cq.proxy.repository.entity.AgentRegistry;
import com.cq.proxy.repository.mapper.AgentRegistryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent推送服务
 * 负责将任务配置推送到Source Agent
 * 符合spec.md设计要求：
 * - 根据source_agent_id查询AgentRegistry获取IP和端口
 * - 通过HTTP POST推送配置到Agent
 * - 验证Agent返回的configPersisted标志
 */
@Service
public class AgentPushService {

    private static final Logger log = LoggerFactory.getLogger(AgentPushService.class);

    private static final String AGENT_CONFIG_ENDPOINT = "/api/batch/task/config";
    private static final String AGENT_CONTROL_ENDPOINT = "/api/batch/task/control";
    private static final int PUSH_TIMEOUT_MS = 5000;

    private final AgentRegistryMapper agentRegistryMapper;
    private final SimpleHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AgentPushService(AgentRegistryMapper agentRegistryMapper, SimpleHttpClient httpClient, ObjectMapper objectMapper) {
        this.agentRegistryMapper = agentRegistryMapper;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 推送任务配置到Agent
     *
     * @param sourceAgentId 源Agent ID
     * @param payload       任务配置JSON
     * @return 是否推送成功
     */
    public boolean pushConfigToAgent(String sourceAgentId, String payload) {
        AgentAddress address = getAgentAddress(sourceAgentId);
        if (address == null) {
            throw new AgentNotFoundException("Agent not found or offline: " + sourceAgentId);
        }

        String url = buildAgentUrl(address, AGENT_CONFIG_ENDPOINT);
        log.info("📤 推送配置到Agent: agentId={}, url={}", sourceAgentId, url);

        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json; charset=utf-8");

            HttpResponse<String> response = httpClient.post(url, payload, headers, String.class);

            if (!response.isSuccess()) {
                log.error("❌ 推送配置失败: agentId={}, statusCode={}", sourceAgentId, response.getStatusCode());
                return false;
            }

            // 解析响应，验证configPersisted标志
            String responseBody = response.getBody();
            if (isConfigPersisted(responseBody)) {
                log.info("✅ Agent确认配置已持久化: agentId={}", sourceAgentId);
                return true;
            }

            log.warn("⚠️ Agent未确认持久化: agentId={}, response={}", sourceAgentId, responseBody);
            return false;

        } catch (Exception e) {
            log.error("❌ 推送配置异常: agentId={}, error={}", sourceAgentId, e.getMessage());
            throw new RuntimeException("Push config failed: " + e.getMessage(), e);
        }
    }

    /**
     * 推送删除指令到Agent
     *
     * @param sourceAgentId 源Agent ID
     * @param payload       删除指令JSON
     * @return 是否推送成功
     */
    public boolean pushDeleteToAgent(String sourceAgentId, String payload) {
        AgentAddress address = getAgentAddress(sourceAgentId);
        if (address == null) {
            throw new AgentNotFoundException("Agent not found or offline: " + sourceAgentId);
        }

        String url = buildAgentUrl(address, AGENT_CONTROL_ENDPOINT);
        log.info("📤 推送删除指令到Agent: agentId={}, url={}", sourceAgentId, url);

        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json; charset=utf-8");

            HttpResponse<String> response = httpClient.post(url, payload, headers, String.class);

            if (!response.isSuccess()) {
                log.error("❌ 推送删除指令失败: agentId={}, statusCode={}", sourceAgentId, response.getStatusCode());
                return false;
            }

            // 解析响应，验证删除成功
            String responseBody = response.getBody();
            if (isDeleteConfirmed(responseBody)) {
                log.info("✅ Agent确认删除指令: agentId={}", sourceAgentId);
                return true;
            }

            log.warn("⚠️ Agent未确认删除: agentId={}, response={}", sourceAgentId, responseBody);
            return false;

        } catch (Exception e) {
            log.error("❌ 推送删除指令异常: agentId={}, error={}", sourceAgentId, e.getMessage());
            throw new RuntimeException("Push delete failed: " + e.getMessage(), e);
        }
    }

    /**
     * 根据agentId查询Agent地址
     *
     * @param agentId Agent ID
     * @return Agent地址，如果不存在或离线则返回null
     */
    public AgentAddress getAgentAddress(String agentId) {
        AgentRegistry registry = agentRegistryMapper.selectByAgentId(agentId);
        if (registry == null) {
            log.warn("⚠️ Agent未注册: agentId={}", agentId);
            return null;
        }

        // 检查Agent是否在线 (nodeStatus=1表示在线)
        if (registry.getNodeStatus() == null || registry.getNodeStatus() != 1) {
            log.warn("⚠️ Agent离线: agentId={}, nodeStatus={}", agentId, registry.getNodeStatus());
            return null;
        }

        return new AgentAddress(registry.getAgentIp(), registry.getAgentPort());
    }

    /**
     * 构建Agent URL
     *
     * @param address  Agent地址
     * @param endpoint API端点
     * @return 完整URL
     */
    public String buildAgentUrl(AgentAddress address, String endpoint) {
        return "http://" + address.getIp() + ":" + address.getPort() + endpoint;
    }

    /**
     * 解析Agent响应，验证configPersisted标志
     * 符合spec.md格式：{"success":true,"data":{"configPersisted":true}}
     *
     * @param responseBody 响应体JSON字符串
     * @return 是否确认持久化
     */
    boolean isConfigPersisted(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            // 验证顶层success=true
            if (!root.path("success").asBoolean(false)) {
                return false;
            }
            // 验证data.configPersisted=true
            JsonNode data = root.path("data");
            return data.path("configPersisted").asBoolean(false);
        } catch (Exception e) {
            log.warn("⚠️ 解析Agent响应失败: {}, error={}", responseBody, e.getMessage());
            return false;
        }
    }

    /**
     * 解析Agent响应，验证删除指令是否被确认
     * 符合spec.md格式：{"success":true,"data":{"deleted":true}}
     *
     * @param responseBody 响应体JSON字符串
     * @return 是否确认删除
     */
    boolean isDeleteConfirmed(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            // 验证顶层success=true
            if (!root.path("success").asBoolean(false)) {
                return false;
            }
            // 验证data.deleted=true
            JsonNode data = root.path("data");
            return data.path("deleted").asBoolean(false);
        } catch (Exception e) {
            log.warn("⚠️ 解析Agent删除响应失败: {}, error={}", responseBody, e.getMessage());
            return false;
        }
    }

    // ==================== 内部类 ====================

    /**
     * Agent地址包装类
     */
    public static class AgentAddress {
        private final String ip;
        private final int port;

        public AgentAddress(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        public String getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }
    }

    /**
     * Agent未找到异常
     */
    public static class AgentNotFoundException extends RuntimeException {
        public AgentNotFoundException(String message) {
            super(message);
        }
    }
}
