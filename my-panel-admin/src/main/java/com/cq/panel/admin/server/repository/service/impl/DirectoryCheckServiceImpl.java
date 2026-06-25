package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.mapper.AgentRegistryMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper;
import com.cq.panel.admin.server.repository.service.IDirectoryCheckService;
import com.cq.panel.admin.server.service.ProxyClientService;
import com.cq.panel.admin.server.web.domain.vo.batch.DirectoryCheckVO;
import com.cq.panel.admin.server.web.domain.dto.proxy.AgentDirCheckData;
import com.cq.panel.admin.server.web.domain.dto.proxy.AgentResponse;
import com.cq.panel.admin.server.web.domain.dto.proxy.ProxyApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DirectoryCheckServiceImpl implements IDirectoryCheckService {

    private static final Logger log = LoggerFactory.getLogger(DirectoryCheckServiceImpl.class);

    private final BatchTransferTaskMapper taskMapper;
    private final AgentRegistryMapper agentRegistryMapper;
    private final ObjectMapper objectMapper;
    private final ProxyClientService proxyClientService;

    public DirectoryCheckServiceImpl(BatchTransferTaskMapper taskMapper,
                                      AgentRegistryMapper agentRegistryMapper,
                                      ObjectMapper objectMapper,
                                      ProxyClientService proxyClientService) {
        this.taskMapper = taskMapper;
        this.agentRegistryMapper = agentRegistryMapper;
        this.objectMapper = objectMapper;
        this.proxyClientService = proxyClientService;
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
            ProxyApiResponse<String> proxyResponse = proxyClientService.dirCheck(agentId, dirPath);

            if (!proxyResponse.isSuccess() || proxyResponse.getData() == null) {
                vo.setExists(false);
                vo.setCanRead(false);
                vo.setCanWrite(false);
                vo.setCanExecute(false);
                vo.setDiskSufficient(false);
                vo.setErrorMessage("Proxy转发失败");
                return vo;
            }

            AgentResponse<AgentDirCheckData> agentResponse = proxyClientService.parseAgentResponse(
                    proxyResponse.getData(),
                    new TypeReference<AgentResponse<AgentDirCheckData>>() {});

            if (!Boolean.TRUE.equals(agentResponse.getSuccess()) || agentResponse.getData() == null) {
                vo.setExists(false);
                vo.setCanRead(false);
                vo.setCanWrite(false);
                vo.setCanExecute(false);
                vo.setDiskSufficient(false);
                vo.setErrorMessage("Agent返回错误: " + agentResponse.getMsg());
                return vo;
            }

            AgentDirCheckData data = agentResponse.getData();

            vo.setExists(data.getExists());
            vo.setIsDirectory(data.getIsDirectory());
            vo.setCanRead(data.getCanRead());
            vo.setCanWrite(data.getCanWrite());
            vo.setCanExecute(data.getCanExecute());

            if (data.getPosixPermissions() != null) {
                vo.setPosixPermissions(data.getPosixPermissions());
            }

            vo.setDiskTotal(data.getDiskTotal());
            vo.setDiskUsable(data.getDiskUsable());
            vo.setDiskFree(data.getDiskFree());
            vo.setDiskTotalMB(data.getDiskTotalMB());
            vo.setDiskUsableMB(data.getDiskUsableMB());
            vo.setDiskFreeMB(data.getDiskFreeMB());
            vo.setDiskSufficient(data.getDiskSufficient());

            if (data.getErrorMessage() != null) {
                vo.setErrorMessage(data.getErrorMessage());
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

    private List<String> parseJsonArray(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isBlank()) return result;
        try {
            String[] items = objectMapper.readValue(json, String[].class);
            if (items != null) {
                for (String item : items) {
                    if (item != null && !item.isBlank()) result.add(item.trim());
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
