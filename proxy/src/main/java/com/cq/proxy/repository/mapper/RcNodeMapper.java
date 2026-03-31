package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.RcNode;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface RcNodeMapper {

  @Select("SELECT * FROM rc_node WHERE env_id = #{envId} AND project_id = #{projectId} AND node_ip = #{nodeIp} AND node_port = #{nodePort}")
  List<RcNode> selectByEnvProjectIpPort(@Param("envId") long envId, @Param("projectId") long projectId, @Param("nodeIp") String nodeIp, @Param("nodePort") int nodePort);

  @Select("SELECT * FROM rc_node WHERE env_id = #{envId} AND project_id = #{projectId} AND status = #{status}")
  List<RcNode> selectByEnvProjectStatus(@Param("envId") long envId, @Param("projectId") long projectId, @Param("status") String status);

  @Select("SELECT * FROM rc_node WHERE status = #{status}")
  List<RcNode> selectByStatus(@Param("status") String status);

  @Insert("INSERT INTO rc_node (env_id, project_id, node_ip, node_port, status, last_refresh_time, create_time, update_time) VALUES (#{envId}, #{projectId}, #{nodeIp}, #{nodePort}, #{status}, #{lastRefreshTime}, #{createTime}, #{updateTime})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(RcNode node);

  @Update("UPDATE rc_node SET status = #{status}, last_refresh_time = #{lastRefreshTime}, update_time = #{updateTime} WHERE id = #{id}")
  int updateById(RcNode node);
}