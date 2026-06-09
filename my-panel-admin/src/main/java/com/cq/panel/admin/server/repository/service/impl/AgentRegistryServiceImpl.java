package com.cq.panel.admin.server.repository.service.impl;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import com.cq.panel.admin.server.common.utils.MyDateUtils;
import com.cq.panel.admin.server.common.utils.SecurityUtils;
import com.cq.panel.common.dto.agent.AgentExecuteCommandRequest;
import com.cq.panel.common.dto.agent.AgentExecuteCommandResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cq.panel.admin.server.repository.mapper.AgentRegistryMapper;
import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.domain.AgentCommandHistory;
import com.cq.panel.admin.server.repository.service.IAgentRegistryService;
import com.cq.panel.admin.server.repository.service.IAgentCommandHistoryService;

import com.cq.panel.admin.server.service.ProxyClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agent注册信息Service业务层处理
 * 
 * @author cq
 */
@Service
public class AgentRegistryServiceImpl implements IAgentRegistryService {

    private final AgentRegistryMapper agentRegistryMapper;
    private final IAgentCommandHistoryService agentCommandHistoryService;
    private final ProxyClientService proxyClientService;

    public AgentRegistryServiceImpl(AgentRegistryMapper agentRegistryMapper, IAgentCommandHistoryService agentCommandHistoryService, ProxyClientService proxyClientService) {
        this.agentRegistryMapper = agentRegistryMapper;
        this.agentCommandHistoryService = agentCommandHistoryService;
        this.proxyClientService = proxyClientService;
    }

    /**
     * 查询Agent注册信息
     * 
     * @param id Agent注册信息主键
     * @return Agent注册信息
     */
    @Override
    public AgentRegistry selectAgentRegistryById(String id)
    {
        return agentRegistryMapper.selectAgentRegistryById(id);
    }

    /**
     * 查询Agent注册信息列表
     * 
     * @param agentRegistry Agent注册信息
     * @return Agent注册信息
     */
    @Override
    public List<AgentRegistry> selectAgentRegistryList(AgentRegistry agentRegistry)
    {
        return agentRegistryMapper.selectAgentRegistryList(agentRegistry);
    }

    /**
     * 新增Agent注册信息
     * 
     * @param agentRegistry Agent注册信息
     * @return 结果
     */
    @Override
    public int insertAgentRegistry(AgentRegistry agentRegistry)
    {
        agentRegistry.setCreateTime(MyDateUtils.getNowDate());
        return agentRegistryMapper.insertAgentRegistry(agentRegistry);
    }

    /**
     * 修改Agent注册信息
     * 
     * @param agentRegistry Agent注册信息
     * @return 结果
     */
    @Override
    public int updateAgentRegistry(AgentRegistry agentRegistry)
    {
        agentRegistry.setUpdateTime(MyDateUtils.getNowDate());
        return agentRegistryMapper.updateAgentRegistry(agentRegistry);
    }

    /**
     * 批量删除Agent注册信息
     * 
     * @param ids 需要删除的Agent注册信息主键
     * @return 结果
     */
    @Override
    public int deleteAgentRegistryByIds(String[] ids)
    {
        return agentRegistryMapper.deleteAgentRegistryByIds(ids);
    }

    /**
     * 删除Agent注册信息信息
     * 
     * @param id Agent注册信息主键
     * @return 结果
     */
    @Override
    public int deleteAgentRegistryById(String id)
    {
        return agentRegistryMapper.deleteAgentRegistryById(id);
    }

    /**
     * Agent注册
     * 
     * @param agentRegistry Agent注册信息
     * @return 结果
     */
    @Override
    @Transactional
    public AgentRegistry registerAgent(AgentRegistry agentRegistry)
    {
        AgentRegistry query = new AgentRegistry();
        query.setAgentIp(agentRegistry.getAgentIp());
        query.setAgentPort(agentRegistry.getAgentPort());
        AgentRegistry existing = agentRegistryMapper.selectAgentRegistryByIpAndPort(query);
        
        Date now = MyDateUtils.getNowDate();
        
        if (existing != null)
        {
            existing.setNodeName(agentRegistry.getNodeName());
            existing.setOsType(agentRegistry.getOsType());
            existing.setAppId(agentRegistry.getAppId());
            existing.setRemark(agentRegistry.getRemark());
            existing.setNodeStatus(1);
            existing.setUpdateTime(now);
            agentRegistryMapper.updateAgentRegistry(existing);
            return existing;
        }
        else
        {
            agentRegistry.setId(UUID.randomUUID().toString().replace("-", ""));
            agentRegistry.setNodeEnabled(0);
            agentRegistry.setNodeStatus(1);
            agentRegistry.setCreateTime(now);
            agentRegistry.setUpdateTime(now);
            agentRegistryMapper.insertAgentRegistry(agentRegistry);
            return agentRegistry;
        }
    }

    /**
     * Agent心跳
     * 
     * @param agentIp Agent IP
     * @param agentPort Agent端口
     * @return 结果
     */
    @Override
    public boolean heartbeat(String agentIp, Integer agentPort)
    {
        AgentRegistry query = new AgentRegistry();
        query.setAgentIp(agentIp);
        query.setAgentPort(agentPort);
        AgentRegistry existing = agentRegistryMapper.selectAgentRegistryByIpAndPort(query);
        
        if (existing != null)
        {
            AgentRegistry update = new AgentRegistry();
            update.setAgentIp(agentIp);
            update.setAgentPort(agentPort);
            update.setNodeStatus(1);
            update.setLastRefreshTime(MyDateUtils.getNowDate());
            update.setUpdateTime(MyDateUtils.getNowDate());
            agentRegistryMapper.updateNodeStatusByIpAndPort(update);
            return true;
        }
        return false;
    }

    /**
     * 下线超时节点
     * 
     * @param timeoutSeconds 超时时间（秒）
     * @return 结果
     */
    @Override
    public int offlineTimeoutNodes(Integer timeoutSeconds)
    {
        Date now = MyDateUtils.getNowDate();
        long timeoutMillis = timeoutSeconds * 1000L;
        
        List<AgentRegistry> onlineNodes = agentRegistryMapper.selectOnlineNodes();
        if (onlineNodes == null || onlineNodes.isEmpty())
        {
            return 0;
        }
        
        int offlineCount = 0;
        for (AgentRegistry node : onlineNodes)
        {
            Date updateTime = node.getUpdateTime();
            if (updateTime == null)
            {
                node.setNodeStatus(0);
                node.setUpdateTime(MyDateUtils.getNowDate());
                agentRegistryMapper.updateAgentRegistry(node);
                offlineCount++;
                continue;
            }
            
            long timeDiff = now.getTime() - updateTime.getTime();
            if (timeDiff > timeoutMillis)
            {
                node.setNodeStatus(0);
                node.setUpdateTime(MyDateUtils.getNowDate());
                agentRegistryMapper.updateAgentRegistry(node);
                offlineCount++;
            }
        }
        
        return offlineCount;
    }

    /**
     * 执行Agent命令
     * 
     * @param agentId Agent节点ID
     * @param command 要执行的命令
     * @param timeout 超时时间（秒）
     * @return 执行结果
     */
    @Override
    public Object executeCommand(String agentId, String command, Integer timeout)
    {
        AgentRegistry agent = agentRegistryMapper.selectAgentRegistryById(agentId);
        if (agent == null)
        {
            throw new RuntimeException("Agent节点不存在");
        }

        if (agent.getNodeStatus() != 1)
        {
            throw new RuntimeException("Agent节点不在线，无法执行命令");
        }

        final AgentExecuteCommandRequest requestBody = new AgentExecuteCommandRequest();
        requestBody.setCommand(command);
        requestBody.setTimeout((long)timeout);

        AgentCommandHistory history = new AgentCommandHistory();
        history.setAgentId(agent.getId());
        history.setAgentName(agent.getNodeName());
        history.setAgentIp(agent.getAgentIp());
        history.setAgentPort(agent.getAgentPort());
        history.setCommand(command);
        history.setCommandTimeout(timeout);
        history.setSubmitTime(MyDateUtils.getNowDate());
        history.setUserId(SecurityUtils.getUserId());
        history.setUserName(SecurityUtils.getUsername());
        try
        {
            String requestJson = new ObjectMapper().writeValueAsString(requestBody);

            Date startTime = MyDateUtils.getNowDate();
            history.setStartTime(startTime);

            String proxyResponse = proxyClientService.executeCommand(agentId, requestJson);
            JsonNode dataNode = proxyClientService.extractData(proxyResponse);

            Date endTime = MyDateUtils.getNowDate();
            long duration = endTime.getTime() - startTime.getTime();
            history.setEndTime(endTime);
            history.setExecuteTime(duration);

            if (dataNode == null)
            {
                history.setCommandStatus(1);
                history.setError("Proxy转发失败");
                agentCommandHistoryService.insert(history);
                throw new RuntimeException("Proxy转发命令执行请求失败");
            }

            // dataNode是Agent原始响应的JSON字符串，需要解析
            AgentExecuteCommandResponse result = new ObjectMapper().readValue(dataNode.asText(), AgentExecuteCommandResponse.class);

            boolean success = result.isSuccess();
            int exitCode = result.getExitCode();
            String output = result.getOutput();
            String error = result.getError();

            if (success)
            {
                history.setCommandStatus(0);
            }
            else if (exitCode == -999)
            {
                history.setCommandStatus(2);
            }
            else if (exitCode < 0)
            {
                history.setCommandStatus(3);
            }
            else
            {
                history.setCommandStatus(1);
            }

            history.setExitCode(exitCode);
            history.setOutput(output);
            history.setError(error);

            agentCommandHistoryService.insert(history);

            return result;
        }
        catch (RuntimeException e)
        {
            if (history.getEndTime() == null)
            {
                history.setEndTime(MyDateUtils.getNowDate());
                if (history.getStartTime() != null)
                {
                    history.setExecuteTime(history.getEndTime().getTime() - history.getStartTime().getTime());
                }
            }
            if (e.getMessage() != null && e.getMessage().contains("超时"))
            {
                history.setCommandStatus(2);
            }
            else
            {
                history.setCommandStatus(1);
            }
            history.setError(e.getMessage());
            agentCommandHistoryService.insert(history);
            throw e;
        }
        catch (Exception e)
        {
            if (history.getEndTime() == null)
            {
                history.setEndTime(MyDateUtils.getNowDate());
                if (history.getStartTime() != null)
                {
                    history.setExecuteTime(history.getEndTime().getTime() - history.getStartTime().getTime());
                }
            }
            history.setCommandStatus(3);
            history.setError(e.getMessage());
            agentCommandHistoryService.insert(history);
            throw new RuntimeException("调用Agent服务失败: " + e.getMessage(), e);
        }
    }
}