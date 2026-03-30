package com.cq.panel.admin.server.repository.service;

import java.util.List;
import com.cq.panel.admin.server.repository.domain.AgentRegistry;

/**
 * Agent注册信息Service接口
 * 
 * @author cq
 */
public interface IAgentRegistryService 
{
    /**
     * 查询Agent注册信息
     * 
     * @param id Agent注册信息主键
     * @return Agent注册信息
     */
    AgentRegistry selectAgentRegistryById(String id);

    /**
     * 查询Agent注册信息列表
     * 
     * @param agentRegistry Agent注册信息
     * @return Agent注册信息集合
     */
    List<AgentRegistry> selectAgentRegistryList(AgentRegistry agentRegistry);

    /**
     * 新增Agent注册信息
     * 
     * @param agentRegistry Agent注册信息
     * @return 结果
     */
    int insertAgentRegistry(AgentRegistry agentRegistry);

    /**
     * 修改Agent注册信息
     * 
     * @param agentRegistry Agent注册信息
     * @return 结果
     */
    int updateAgentRegistry(AgentRegistry agentRegistry);

    /**
     * 批量删除Agent注册信息
     * 
     * @param ids 需要删除的Agent注册信息主键集合
     * @return 结果
     */
    int deleteAgentRegistryByIds(String[] ids);

    /**
     * 删除Agent注册信息信息
     * 
     * @param id Agent注册信息主键
     * @return 结果
     */
    int deleteAgentRegistryById(String id);

    /**
     * Agent注册
     * 
     * @param agentRegistry Agent注册信息
     * @return 结果
     */
    AgentRegistry registerAgent(AgentRegistry agentRegistry);

    /**
     * Agent心跳
     * 
     * @param agentIp Agent IP
     * @param agentPort Agent端口
     * @return 结果
     */
    boolean heartbeat(String agentIp, Integer agentPort);

    /**
     * 下线超时节点
     * 
     * @param timeoutSeconds 超时时间（秒）
     * @return 结果
     */
    int offlineTimeoutNodes(Integer timeoutSeconds);
}