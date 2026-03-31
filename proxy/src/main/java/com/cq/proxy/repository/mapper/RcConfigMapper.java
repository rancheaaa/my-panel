package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.RcConfig;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RcConfigMapper {

  @Select("SELECT * FROM rc_config WHERE env_id = #{envId} AND project_id = #{projectId} AND config_key = #{configKey} LIMIT 1")
  RcConfig selectByEnvProjectKey(@Param("envId") long envId, @Param("projectId") long projectId, @Param("configKey") String configKey);

  @Insert("INSERT INTO rc_config (env_id, project_id, config_key, config_value, config_desc, source, create_time, update_time) VALUES (#{envId}, #{projectId}, #{configKey}, #{configValue}, #{configDesc}, #{source}, #{createTime}, #{updateTime})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(RcConfig config);

  @Update("UPDATE rc_config SET config_value = #{configValue}, config_desc = #{configDesc}, update_time = #{updateTime} WHERE id = #{id}")
  int updateById(RcConfig config);
}