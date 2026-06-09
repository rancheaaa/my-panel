package com.cq.panel.admin.server.service.batch;

import com.cq.panel.admin.server.service.ProxyClientService;
import com.cq.panel.admin.server.web.domain.dto.proxy.AgentResponse;
import com.cq.panel.admin.server.web.domain.dto.proxy.ProxyApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent目录检查工具类
 * 通过Proxy转发到Agent的 /api/file/exists?path=xxx 接口检查目录是否存在
 */
@Component
public class AgentDirectoryChecker {

    private static final Logger log = LoggerFactory.getLogger(AgentDirectoryChecker.class);

    private final ProxyClientService proxyClientService;

    public AgentDirectoryChecker(ProxyClientService proxyClientService) {
        this.proxyClientService = proxyClientService;
    }

    /**
     * 检查指定Agent上的目录是否存在
     *
     * @param agentId 节点ID
     * @param dirPath 目录路径
     * @return true-目录存在, false-目录不存在, null-检查失败(如Agent离线/网络异常/路径为空)
     */
    public Boolean checkDirectoryExists(String agentId, String dirPath) {
        if (dirPath == null || dirPath.trim().isEmpty()) {
            log.debug("目录路径为空，跳过检查: agentId={}", agentId);
            return null;
        }

        log.debug("检查目录: agentId={}, path={}", agentId, dirPath);

        try {
            ProxyApiResponse<String> proxyResponse = proxyClientService.fileExists(agentId, dirPath);

            if (!proxyResponse.isSuccess() || proxyResponse.getData() == null) {
                log.warn("Proxy转发失败: agentId={}, path={}", agentId, dirPath);
                return null;
            }

            AgentResponse<Boolean> agentResponse = proxyClientService.parseAgentResponse(
                    proxyResponse.getData(),
                    new TypeReference<AgentResponse<Boolean>>() {});

            if (agentResponse.getData() == null) {
                log.warn("Agent响应无data字段: agentId={}", agentId);
                return null;
            }

            boolean exists = Boolean.TRUE.equals(agentResponse.getData());
            log.debug("目录检查结果: agentId={}, path={}, exists={}", agentId, dirPath, exists);
            return exists;

        } catch (Exception e) {
            log.warn("检查目录失败: agentId={}, path={}, error={}", agentId, dirPath, e.getMessage());
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
