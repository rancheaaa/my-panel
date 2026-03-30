package com.cq.proxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cq.proxy.api.dto.ConfigUpdateRequest;
import com.cq.proxy.api.dto.ConfigValueResponse;
import com.cq.proxy.exception.BusinessException;
import com.cq.proxy.repository.entity.RcConfig;
import com.cq.proxy.repository.mapper.RcConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigService {

  private final RcDictionaryService dictionaryService;
  private final RcConfigMapper configMapper;
  private final Clock clock;
  private final Cache<String, ConfigValueResponse> configCache;

  public ConfigService(
      RcDictionaryService dictionaryService,
      RcConfigMapper configMapper,
      Clock clock,
      Caffeine<Object, Object> caffeine
  ) {
    this.dictionaryService = dictionaryService;
    this.configMapper = configMapper;
    this.clock = clock;
    this.configCache = caffeine.build();
  }

  public ConfigValueResponse getConfig(String configKey, String environment, String serviceName) {
    if (configKey == null || configKey.isBlank()) {
      throw new BusinessException(400, "configKey is required");
    }
    if (environment == null || environment.isBlank()) {
      throw new BusinessException(400, "environment is required");
    }
    if (serviceName == null || serviceName.isBlank()) {
      throw new BusinessException(400, "serviceName is required");
    }

    String key = cacheKey(serviceName, environment, configKey);
    ConfigValueResponse cached = configCache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }

    long envId = dictionaryService.getOrCreateEnvId(environment);
    long projectId = dictionaryService.getOrCreateProjectId(serviceName);

    RcConfig config = configMapper.selectOne(new LambdaQueryWrapper<RcConfig>()
        .eq(RcConfig::getEnvId, envId)
        .eq(RcConfig::getProjectId, projectId)
        .eq(RcConfig::getConfigKey, configKey));

    if (config == null) {
      throw new BusinessException(404, "config not found");
    }

    ConfigValueResponse response = new ConfigValueResponse(configKey, config.getConfigValue(), config.getConfigDesc());
    configCache.put(key, response);
    return response;
  }

  @Transactional
  public ConfigValueResponse updateConfig(String configKey, ConfigUpdateRequest request) {
    if (configKey == null || configKey.isBlank()) {
      throw new BusinessException(400, "configKey is required");
    }
    if (request == null) {
      throw new BusinessException(400, "request body is required");
    }

    long envId = dictionaryService.getOrCreateEnvId(request.environment());
    long projectId = dictionaryService.getOrCreateProjectId(request.serviceName());

    RcConfig existing = configMapper.selectOne(new LambdaQueryWrapper<RcConfig>()
        .eq(RcConfig::getEnvId, envId)
        .eq(RcConfig::getProjectId, projectId)
        .eq(RcConfig::getConfigKey, configKey));

    LocalDateTime now = LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
    if (existing == null) {
      RcConfig config = new RcConfig();
      config.setEnvId(envId);
      config.setProjectId(projectId);
      config.setConfigKey(configKey);
      config.setConfigValue(request.configValue());
      config.setConfigDesc(request.configDesc());
      config.setSource("0");
      config.setCreateTime(now);
      config.setUpdateTime(now);
      configMapper.insert(config);
    } else {
      existing.setConfigValue(request.configValue());
      existing.setConfigDesc(request.configDesc());
      existing.setUpdateTime(now);
      configMapper.updateById(existing);
    }

    ConfigValueResponse response = new ConfigValueResponse(configKey, request.configValue(), request.configDesc());
    configCache.put(cacheKey(request.serviceName(), request.environment(), configKey), response);
    return response;
  }

  public void invalidate(String serviceName, String environment, String configKey) {
    configCache.invalidate(cacheKey(serviceName, environment, configKey));
  }

  private static String cacheKey(String serviceName, String environment, String configKey) {
    return serviceName + "|" + environment + "|" + configKey;
  }
}

