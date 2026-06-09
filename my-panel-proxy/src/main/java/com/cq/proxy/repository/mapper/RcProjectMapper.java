package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.RcProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RcProjectMapper {

    RcProject selectByName(@Param("projectName") String projectName);

    int insert(RcProject project);

    int updateById(RcProject project);
}
