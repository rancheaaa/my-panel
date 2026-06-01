package com.cq.panel.admin.server.repository.mapper;

import java.util.List;
import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import org.apache.ibatis.annotations.Param;

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
    AgentRegistry selectAgentRegistryById(String id);

    /**
     * 查询Agent注册信息列表
     * 
     * @param agentRegistry Agent注册信息
     * @return Agent注册信息集合
     */
    List<AgentRegistry> selectAgentRegistryList(AgentRegistry agentRegistry);

    /**
     * 根据IP和端口查询Agent注册信息
     * 
     * @param agentRegistry Agent注册信息
     * @return Agent注册信息
     */
    AgentRegistry selectAgentRegistryByIpAndPort(AgentRegistry agentRegistry);

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
     * 删除Agent注册信息
     * 
     * @param id Agent注册信息主键
     * @return 结果
     */
    int deleteAgentRegistryById(String id);

    /**
     * 批量删除Agent注册信息
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteAgentRegistryByIds(String[] ids);

    /**
     * 根据IP和端口更新节点状态
     * 
     * @param agentRegistry Agent注册信息
     * @return 结果
     */
    int updateNodeStatusByIpAndPort(AgentRegistry agentRegistry);

    /**
     * 查询在线节点列表（用于超时检测）
     * 
     * @return 在线节点列表
     */
    List<AgentRegistry> selectOnlineNodes();

    /**
     * 根据ID列表批量查询Agent注册信息
     * 
     * @param ids Agent ID列表
     * @return Agent注册信息集合
     */
    List<AgentRegistry> selectAgentRegistryByIds(@Param("ids") List<String> ids);

    AgentRegistry selectByNodeName(@Param("nodeName") String nodeName);
}