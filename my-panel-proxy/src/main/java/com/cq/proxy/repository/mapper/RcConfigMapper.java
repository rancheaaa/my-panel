package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.RcConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RcConfigMapper {

    RcConfig selectByEnvProjectKey(@Param("envId") long envId, @Param("projectId") long projectId, @Param("configKey") String configKey);

    int insert(RcConfig config);

    int updateById(RcConfig config);
}
