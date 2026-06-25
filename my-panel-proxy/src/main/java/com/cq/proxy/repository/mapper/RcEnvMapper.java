package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.RcEnv;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RcEnvMapper {

    RcEnv selectByName(@Param("envName") String envName);

    int insert(RcEnv env);

    int updateById(RcEnv env);
}
