package com.cq.panel.admin.server.repository.service.impl;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import com.cq.panel.admin.server.common.utils.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cq.panel.admin.server.repository.mapper.AgentRegistryMapper;
import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.service.IAgentRegistryService;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent注册信息Service业务层处理
 * 
 * @author cq
 */
@Service
public class AgentRegistryServiceImpl implements IAgentRegistryService {

    private final AgentRegistryMapper agentRegistryMapper;

    public AgentRegistryServiceImpl(AgentRegistryMapper agentRegistryMapper) {
        this.agentRegistryMapper = agentRegistryMapper;
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
        agentRegistry.setCreateTime(DateUtils.getNowDate());
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
        agentRegistry.setUpdateTime(DateUtils.getNowDate());
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
        
        Date now = DateUtils.getNowDate();
        
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
            update.setUpdateTime(DateUtils.getNowDate());
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
        Date now = DateUtils.getNowDate();
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
                node.setUpdateTime(DateUtils.getNowDate());
                agentRegistryMapper.updateAgentRegistry(node);
                offlineCount++;
                continue;
            }
            
            long timeDiff = now.getTime() - updateTime.getTime();
            if (timeDiff > timeoutMillis)
            {
                node.setNodeStatus(0);
                node.setUpdateTime(DateUtils.getNowDate());
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
        // 1. 根据agentId查询Agent信息
        AgentRegistry agent = agentRegistryMapper.selectAgentRegistryById(agentId);
        if (agent == null)
        {
            throw new RuntimeException("Agent节点不存在");
        }
        
        // 2. 检查Agent是否在线
        if (agent.getNodeStatus() != 1)
        {
            throw new RuntimeException("Agent节点不在线，无法执行命令");
        }
        
        // 3. 构建Agent API URL
        String agentUrl = "http://" + agent.getAgentIp() + ":" + agent.getAgentPort() + "/api/execute";
        
        // 4. 准备请求参数
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("command", command);
        requestBody.put("timeout", timeout);
        
        // 5. 发送HTTP请求到Agent
        try
        {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            
            // 设置超时时间
            ResponseEntity<String> response = restTemplate.postForEntity(agentUrl, requestEntity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK)
            {
                // 解析Agent返回的JSON响应
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readValue(response.getBody(), Object.class);
            }
            else
            {
                throw new RuntimeException("Agent服务返回错误状态码: " + response.getStatusCode());
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("调用Agent服务失败: " + e.getMessage(), e);
        }
    }
}