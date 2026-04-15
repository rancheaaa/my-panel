package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.RcProject;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RcProjectMapper {

  @Select("SELECT * FROM rc_project WHERE project_name = #{projectName} LIMIT 1")
  RcProject selectByName(@Param("projectName") String projectName);

  @Insert("INSERT INTO rc_project (project_name, create_time, update_time) VALUES (#{projectName}, #{createTime}, #{updateTime})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(RcProject project);

  @Update("UPDATE rc_project SET project_name = #{projectName}, update_time = #{updateTime} WHERE id = #{id}")
  int updateById(RcProject project);
}