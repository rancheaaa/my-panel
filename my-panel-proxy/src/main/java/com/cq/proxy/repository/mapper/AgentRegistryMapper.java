package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.AgentRegistry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentRegistryMapper {

    AgentRegistry selectByIpPort(@Param("agentIp") String agentIp, @Param("agentPort") int agentPort);

    AgentRegistry selectByAgentId(@Param("agentId") String agentId);

    int insert(AgentRegistry agentRegistry);

    int updateById(AgentRegistry agentRegistry);
}
