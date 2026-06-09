package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.RcNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RcNodeMapper {

    List<RcNode> selectByEnvProjectIpPort(@Param("envId") long envId, @Param("projectId") long projectId, @Param("nodeIp") String nodeIp, @Param("nodePort") int nodePort);

    List<RcNode> selectByEnvProjectStatus(@Param("envId") long envId, @Param("projectId") long projectId, @Param("status") String status);

    List<RcNode> selectByStatus(@Param("status") String status);

    int insert(RcNode node);

    int updateById(RcNode node);
}
