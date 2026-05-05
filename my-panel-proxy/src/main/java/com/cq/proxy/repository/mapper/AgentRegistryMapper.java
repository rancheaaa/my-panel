package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.AgentRegistry;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AgentRegistryMapper {

  @Select("SELECT * FROM agent_registry WHERE id = #{id} LIMIT 1")
  AgentRegistry selectById(@Param("id") String id);

  @Select("SELECT * FROM agent_registry WHERE agent_ip = #{agentIp} AND agent_port = #{agentPort} LIMIT 1")
  AgentRegistry selectByIpPort(@Param("agentIp") String agentIp, @Param("agentPort") int agentPort);

  @Insert("INSERT INTO agent_registry (id, node_name, os_type, app_id, agent_ip, agent_port, node_enabled, node_status, remark, last_refresh_time, create_time, update_time) VALUES (#{id}, #{nodeName}, #{osType}, #{appId}, #{agentIp}, #{agentPort}, #{nodeEnabled}, #{nodeStatus}, #{remark}, #{lastRefreshTime}, #{createTime}, #{updateTime})")
  int insert(AgentRegistry agentRegistry);

  @Update("UPDATE agent_registry SET node_name = #{nodeName}, os_type = #{osType}, app_id = #{appId}, node_enabled = #{nodeEnabled}, node_status = #{nodeStatus}, remark = #{remark}, last_refresh_time = #{lastRefreshTime}, update_time = #{updateTime} WHERE id = #{id}")
  int updateById(AgentRegistry agentRegistry);
}