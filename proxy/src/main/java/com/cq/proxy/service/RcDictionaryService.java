package com.cq.proxy.service;

import com.cq.proxy.exception.BusinessException;
import com.cq.proxy.repository.entity.RcEnv;
import com.cq.proxy.repository.entity.RcProject;
import com.cq.proxy.repository.mapper.RcEnvMapper;
import com.cq.proxy.repository.mapper.RcProjectMapper;
import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RcDictionaryService {

  private final RcEnvMapper envMapper;
  private final RcProjectMapper projectMapper;

  public RcDictionaryService(RcEnvMapper envMapper, RcProjectMapper projectMapper) {
    this.envMapper = envMapper;
    this.projectMapper = projectMapper;
  }

  @Transactional
  public long getEnvId(String envName) {
    if (envName == null || envName.isBlank()) {
      throw new BusinessException(400, "environment is required");
    }
    RcEnv existing = envMapper.selectByName(envName);
    if (existing != null && existing.getId() != null) {
      return existing.getId();
    }
    throw new IllegalStateException("there is no environment with name " + envName);
  }

  @Transactional
  public long getProjectId(String projectName) {
    if (projectName == null || projectName.isBlank()) {
      throw new BusinessException(400, "serviceName is required");
    }
    RcProject existing = projectMapper.selectByName(projectName);
    if (existing != null && existing.getId() != null) {
      return existing.getId();
    }
    throw new IllegalStateException("there  is no project with name " + projectName);
  }
}