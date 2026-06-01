package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.mapper.AgentRegistryMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper;
import com.cq.panel.admin.server.repository.service.IDirectoryCheckService;
import com.cq.panel.admin.server.web.domain.vo.batch.DirectoryCheckVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class DirectoryCheckServiceImpl implements IDirectoryCheckService {

    private static final Logger log = LoggerFactory.getLogger(DirectoryCheckServiceImpl.class);
    private static final int HTTP_TIMEOUT_S = 10;

    private final BatchTransferTaskMapper taskMapper;
    private final AgentRegistryMapper agentRegistryMapper;
    private final ObjectMapper objectMapper;

    public DirectoryCheckServiceImpl(BatchTransferTaskMapper taskMapper,
                                      AgentRegistryMapper agentRegistryMapper,
                                      ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.agentRegistryMapper = agentRegistryMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public DirectoryCheckVO checkSingleDirectory(String agentId, String dirPath) {
        return checkAgentDirectory(agentId, dirPath, "manual");
    }

    @Override
    public DirectoryCheckVO.DirectoryCheckResult checkDirectories(Long taskId) {
        BatchTransferTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        DirectoryCheckVO.DirectoryCheckResult result = new DirectoryCheckVO.DirectoryCheckResult();

        result.setSource(checkAgentDirectory(task.getSourceAgentId(), task.getSourceDir(), "source"));

        List<DirectoryCheckVO> targetResults = new ArrayList<>();
        List<String> targetAgentIds = parseJsonArray(task.getTargetAgentIds());
        List<String> targetDirs = parseJsonArray(task.getTargetDirs());

        for (int i = 0; i < targetAgentIds.size(); i++) {
            String agentId = targetAgentIds.get(i);
            String dirPath = i < targetDirs.size() ? targetDirs.get(i) : "";
            targetResults.add(checkAgentDirectory(agentId, dirPath, "target"));
        }

        result.setTargets(targetResults);
        return result;
    }

    private DirectoryCheckVO checkAgentDirectory(String agentId, String dirPath, String role) {
        DirectoryCheckVO vo = new DirectoryCheckVO();
        vo.setAgentId(agentId);
        vo.setDirPath(dirPath);
        vo.setRole(role);

        AgentRegistry agent = agentRegistryMapper.selectAgentRegistryById(agentId);
        if (agent == null) {
            vo.setAgentOnline(false);
            vo.setExists(false);
            vo.setCanRead(false);
            vo.setCanWrite(false);
            vo.setCanExecute(false);
            vo.setDiskSufficient(false);
            vo.setErrorMessage("节点不存在: " + agentId);
            return vo;
        }

        vo.setAgentName(agent.getNodeName());
        vo.setAgentIp(agent.getAgentIp());
        vo.setAgentPort(agent.getAgentPort());

        boolean online = agent.getNodeStatus() != null && agent.getNodeStatus() == 1;
        vo.setAgentOnline(online);

        if (!online) {
            vo.setExists(false);
            vo.setCanRead(false);
            vo.setCanWrite(false);
            vo.setCanExecute(false);
            vo.setDiskSufficient(false);
            vo.setErrorMessage("节点离线");
            return vo;
        }

        try {
            String url = UriComponentsBuilder.newInstance()
                    .scheme("http")
                    .host(agent.getAgentIp())
                    .port(agent.getAgentPort())
                    .path("/api/file/dir-check")
                    .queryParam("path", dirPath)
                    .build()
                    .toUriString();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_S))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_S))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.path("data");

                vo.setExists(getBoolean(data, "exists"));
                vo.setIsDirectory(getBoolean(data, "isDirectory"));
                vo.setCanRead(getBoolean(data, "canRead"));
                vo.setCanWrite(getBoolean(data, "canWrite"));
                vo.setCanExecute(getBoolean(data, "canExecute"));

                if (data.has("posixPermissions")) {
                    vo.setPosixPermissions(data.get("posixPermissions").asText());
                }

                vo.setDiskTotal(getLong(data, "diskTotal"));
                vo.setDiskUsable(getLong(data, "diskUsable"));
                vo.setDiskFree(getLong(data, "diskFree"));
                vo.setDiskTotalMB(getLong(data, "diskTotalMB"));
                vo.setDiskUsableMB(getLong(data, "diskUsableMB"));
                vo.setDiskFreeMB(getLong(data, "diskFreeMB"));
                vo.setDiskSufficient(getBoolean(data, "diskSufficient"));

                if (data.has("errorMessage")) {
                    vo.setErrorMessage(data.get("errorMessage").asText());
                }
            } else {
                vo.setExists(false);
                vo.setCanRead(false);
                vo.setCanWrite(false);
                vo.setCanExecute(false);
                vo.setDiskSufficient(false);
                vo.setErrorMessage("Agent返回HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            log.warn("目录检测失败: agentId={}, path={}, error={}", agentId, dirPath, e.getMessage());
            vo.setExists(false);
            vo.setCanRead(false);
            vo.setCanWrite(false);
            vo.setCanExecute(false);
            vo.setDiskSufficient(false);
            vo.setErrorMessage("检测失败: " + e.getMessage());
        }

        return vo;
    }

    private Boolean getBoolean(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asBoolean() : null;
    }

    private Long getLong(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asLong() : null;
    }

    private List<String> parseJsonArray(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isBlank()) return result;
        try {
            JsonNode array = objectMapper.readTree(json);
            if (array.isArray()) {
                for (JsonNode item : array) {
                    result.add(item.asText());
                }
            }
        } catch (Exception e) {
            for (String s : json.split(";")) {
                if (!s.isBlank()) result.add(s.trim());
            }
        }
        return result;
    }
}
