package com.cq.proxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cq.proxy.exception.BusinessException;
import com.cq.proxy.repository.entity.RcEnv;
import com.cq.proxy.repository.entity.RcProject;
import com.cq.proxy.repository.mapper.RcEnvMapper;
import com.cq.proxy.repository.mapper.RcProjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RcDictionaryService {

  private final RcEnvMapper envMapper;
  private final RcProjectMapper projectMapper;
  private final Clock clock;

  public RcDictionaryService(RcEnvMapper envMapper, RcProjectMapper projectMapper, Clock clock) {
    this.envMapper = envMapper;
    this.projectMapper = projectMapper;
    this.clock = clock;
  }

  @Transactional
  public long getOrCreateEnvId(String envName) {
    if (envName == null || envName.isBlank()) {
      throw new BusinessException(400, "environment is required");
    }
    RcEnv existing = envMapper.selectOne(new LambdaQueryWrapper<RcEnv>().eq(RcEnv::getEnvName, envName));
    if (existing != null && existing.getId() != null) {
      return existing.getId();
    }

    RcEnv env = new RcEnv();
    env.setEnvName(envName);
    LocalDateTime now = LocalDateTime.now(clock);
    env.setCreateTime(now);
    env.setUpdateTime(now);
    envMapper.insert(env);
    if (env.getId() == null) {
      throw new BusinessException(500, "failed to create environment");
    }
    return env.getId();
  }

  @Transactional
  public long getOrCreateProjectId(String projectName) {
    if (projectName == null || projectName.isBlank()) {
      throw new BusinessException(400, "serviceName is required");
    }
    RcProject existing = projectMapper.selectOne(new LambdaQueryWrapper<RcProject>().eq(RcProject::getProjectName, projectName));
    if (existing != null && existing.getId() != null) {
      return existing.getId();
    }

    RcProject project = new RcProject();
    project.setProjectName(projectName);
    LocalDateTime now = LocalDateTime.now(clock);
    project.setCreateTime(now);
    project.setUpdateTime(now);
    projectMapper.insert(project);
    if (project.getId() == null) {
      throw new BusinessException(500, "failed to create project");
    }
    return project.getId();
  }
}

