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
        return agentRegistryMapper.updateNodeOfflineByTimeout(timeoutSeconds);
    }
}