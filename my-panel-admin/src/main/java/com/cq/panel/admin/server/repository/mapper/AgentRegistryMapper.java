package com.cq.panel.admin.server.repository.mapper;

import java.util.List;
import com.cq.panel.admin.server.repository.domain.AgentRegistry;

/**
 * Agent注册信息Mapper接口
 * 
 * @author cq
 */
public interface AgentRegistryMapper 
{
    /**
     * 查询Agent注册信息
     * 
     * @param id Agent注册信息主键
     * @return Agent注册信息
     */
    public AgentRegistry selectAgentRegistryById(String id);

    /**
     * 查询Agent注册信息列表
     * 
     * @param agentRegistry Agent注册信息
     * @return Agent注册信息集合
     */
    public List<AgentRegistry> selectAgentRegistryList(AgentRegistry agentRegistry);

    /**
     * 根据IP和端口查询Agent注册信息
     * 
     * @param agentRegistry Agent注册信息
     * @return Agent注册信息
     */
    public AgentRegistry selectAgentRegistryByIpAndPort(AgentRegistry agentRegistry);

    /**
     * 新增Agent注册信息
     * 
     * @param agentRegistry Agent注册信息
     * @return 结果
     */
    public int insertAgentRegistry(AgentRegistry agentRegistry);

    /**
     * 修改Agent注册信息
     * 
     * @param agentRegistry Agent注册信息
     * @return 结果
     */
    public int updateAgentRegistry(AgentRegistry agentRegistry);

    /**
     * 删除Agent注册信息
     * 
     * @param id Agent注册信息主键
     * @return 结果
     */
    public int deleteAgentRegistryById(String id);

    /**
     * 批量删除Agent注册信息
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAgentRegistryByIds(String[] ids);

    /**
     * 根据IP和端口更新节点状态
     * 
     * @param agentRegistry Agent注册信息
     * @return 结果
     */
    public int updateNodeStatusByIpAndPort(AgentRegistry agentRegistry);

    /**
     * 批量下线超时节点
     * 
     * @param timeoutSeconds 超时时间（秒）
     * @return 结果
     */
    public int updateNodeOfflineByTimeout(Integer timeoutSeconds);
}