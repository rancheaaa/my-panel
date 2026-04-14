package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.RcEnv;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RcEnvMapper {

  @Select("SELECT * FROM rc_env WHERE env_name = #{envName} LIMIT 1")
  RcEnv selectByName(@Param("envName") String envName);

  @Insert("INSERT INTO rc_env (env_name, create_time, update_time) VALUES (#{envName}, #{createTime}, #{updateTime})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(RcEnv env);

  @Update("UPDATE rc_env SET env_name = #{envName}, update_time = #{updateTime} WHERE id = #{id}")
  int updateById(RcEnv env);
}