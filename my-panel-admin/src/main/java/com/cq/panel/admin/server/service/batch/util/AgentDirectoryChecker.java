package com.cq.panel.admin.server.service.batch.util;

import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.mapper.AgentRegistryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent目录检查工具类
 * 通过HTTP调用Agent的 /api/file/exists?path=xxx 接口检查目录是否存在
 *
 * @author cq
 */
@Component
public class AgentDirectoryChecker {

    private static final Logger log = LoggerFactory.getLogger(AgentDirectoryChecker.class);

    private final RestTemplate restTemplate;
    private final AgentRegistryMapper agentRegistryMapper;
    private final ObjectMapper objectMapper;

    public AgentDirectoryChecker(RestTemplate restTemplate,
                                  AgentRegistryMapper agentRegistryMapper,
                                  ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.agentRegistryMapper = agentRegistryMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 检查指定Agent上的目录是否存在
     *
     * @param agentId 节点ID
     * @param dirPath 目录路径
     * @return true-目录存在, false-目录不存在, null-检查失败(如Agent离线/网络异常/路径为空)
     */
    public Boolean checkDirectoryExists(String agentId, String dirPath) {
        AgentRegistry agent = agentRegistryMapper.selectAgentRegistryById(agentId);
        if (agent == null) {
            log.warn("⚠️ Agent未注册: agentId={}", agentId);
            return null;
        }
        if (agent.getNodeStatus() == null || agent.getNodeStatus() != 1) {
            log.debug("Agent离线，跳过目录检查: agentId={}, status={}", agentId, agent.getNodeStatus());
            return null;
        }
        if (dirPath == null || dirPath.trim().isEmpty()) {
            log.debug("目录路径为空，跳过检查: agentId={}", agentId);
            return null;
        }

        // 使用 UriComponentsBuilder 构建URL，RestTemplate会自动编码参数
        String url = UriComponentsBuilder.newInstance()
                .scheme("http")
                .host(agent.getAgentIp())
                .port(agent.getAgentPort())
                .path("/api/file/exists")
                .queryParam("path", dirPath)
                .build()
                .toUriString();

        log.debug("检查目录: agentId={}, ip={}, port={}, path={}, url={}",
                agentId, agent.getAgentIp(), agent.getAgentPort(), dirPath, url);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("⚠️ 检查目录失败-HTTP错误: agentId={}, status={}, url={}",
                        agentId, response.getStatusCode(), url);
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode dataNode = root.get("data");

            if (dataNode == null) {
                log.warn("️ 检查目录失败-无data字段: agentId={}, response={}",
                        agentId, response.getBody());
                return null;
            }

            boolean exists = dataNode.asBoolean();
            log.debug("目录检查结果: agentId={}, path={}, exists={}", agentId, dirPath, exists);
            return exists;

        } catch (ResourceAccessException e) {
            log.warn("⚠️ 检查目录失败-连接异常: agentId={}, ip={}, port={}, error={}",
                    agentId, agent.getAgentIp(), agent.getAgentPort(), e.getMessage());
            return null;
        } catch (RestClientException e) {
            log.warn("⚠️ 检查目录失败-REST异常: agentId={}, url={}, error={}",
                    agentId, url, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("⚠️ 检查目录失败-未知异常: agentId={}, path={}, error={}",
                    agentId, dirPath, e.getMessage());
            return null;
        }
    }

    /**
     * 批量检查多个节点上的目录是否存在
     *
     * @param agentIds 节点ID列表
     * @param dirPaths 目录路径列表
     * @return 每个节点对应的检查结果
     */
    public Map<String, Boolean> batchCheckDirectoryExists(List<String> agentIds, List<String> dirPaths) {
        Map<String, Boolean> results = new HashMap<>();

        for (int i = 0; i < agentIds.size(); i++) {
            String agentId = agentIds.get(i);
            String dirPath = i < dirPaths.size() ? dirPaths.get(i) : "";
            results.put(agentId, checkDirectoryExists(agentId, dirPath));
        }

        return results;
    }
}
