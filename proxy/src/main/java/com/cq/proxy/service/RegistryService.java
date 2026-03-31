package com.cq.proxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cq.proxy.api.dto.AgentRegisterRequest;
import com.cq.proxy.api.dto.AgentRegisterResponse;
import com.cq.proxy.api.dto.ServiceInstance;
import com.cq.proxy.api.dto.ServiceRegisterRequest;
import com.cq.proxy.config.ProxyRegistryProperties;
import com.cq.proxy.exception.BusinessException;
import com.cq.proxy.repository.entity.AgentRegistry;
import com.cq.proxy.repository.entity.RcNode;
import com.cq.proxy.repository.mapper.AgentRegistryMapper;
import com.cq.proxy.repository.mapper.RcNodeMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistryService {

  private final RcDictionaryService dictionaryService;
  private final RcNodeMapper nodeMapper;
  private final AgentRegistryMapper agentRegistryMapper;
  private final ProxyRegistryProperties registryProperties;
  private final Clock clock;
  private final Cache<String, List<ServiceInstance>> discoverCache;
  private final DateTimeFormatter dateTimeFormatter;

  public RegistryService(
      RcDictionaryService dictionaryService,
      RcNodeMapper nodeMapper,
      AgentRegistryMapper agentRegistryMapper,
      ProxyRegistryProperties registryProperties,
      Clock clock,
      Caffeine<Object, Object> caffeine
  ) {
    this.dictionaryService = dictionaryService;
    this.nodeMapper = nodeMapper;
    this.agentRegistryMapper = agentRegistryMapper;
    this.registryProperties = registryProperties;
    this.clock = clock;
    this.discoverCache = caffeine.build();
    this.dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  }

  @Transactional
  public void register(ServiceRegisterRequest request) {
    if (request == null) {
      throw new BusinessException(400, "request body is required");
    }
    if (request.host() == null || request.host().isBlank()) {
      throw new BusinessException(400, "host is required");
    }
    if (request.port() <= 0 || request.port() > 65535) {
      throw new BusinessException(400, "port is invalid");
    }

    long envId = dictionaryService.getOrCreateEnvId(request.environment());
    long projectId = dictionaryService.getOrCreateProjectId(request.serviceName());

    RcNode existing = nodeMapper.selectOne(new LambdaQueryWrapper<RcNode>()
        .eq(RcNode::getEnvId, envId)
        .eq(RcNode::getProjectId, projectId)
        .eq(RcNode::getNodeIp, request.host())
        .eq(RcNode::getNodePort, request.port()));

    LocalDateTime now = LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
    if (existing == null) {
      RcNode node = new RcNode();
      node.setEnvId(envId);
      node.setProjectId(projectId);
      node.setNodeIp(request.host());
      node.setNodePort(request.port());
      node.setStatus("0");
      node.setLastRefreshTime(now);
      node.setCreateTime(now);
      node.setUpdateTime(now);
      nodeMapper.insert(node);
    } else {
      existing.setStatus("0");
      existing.setLastRefreshTime(now);
      existing.setUpdateTime(now);
      nodeMapper.updateById(existing);
    }

    discoverCache.invalidate(cacheKey(request.serviceName(), request.environment()));
  }

  public List<ServiceInstance> discover(String serviceName, String environment) {
    if (serviceName == null || serviceName.isBlank()) {
      throw new BusinessException(400, "serviceName is required");
    }
    if (environment == null || environment.isBlank()) {
      throw new BusinessException(400, "environment is required");
    }
    String key = cacheKey(serviceName, environment);
    List<ServiceInstance> cached = discoverCache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }

    long envId = dictionaryService.getOrCreateEnvId(environment);
    long projectId = dictionaryService.getOrCreateProjectId(serviceName);
    LocalDateTime aliveThreshold = LocalDateTime.ofInstant(
        Instant.now(clock).minusSeconds(registryProperties.heartbeatTimeoutSeconds()),
        ZoneOffset.UTC);

    List<RcNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<RcNode>()
        .eq(RcNode::getEnvId, envId)
        .eq(RcNode::getProjectId, projectId)
        .eq(RcNode::getStatus, "0"));

    List<ServiceInstance> instances = new ArrayList<>();
    for (RcNode node : nodes) {
      boolean available = node.getLastRefreshTime() != null && !node.getLastRefreshTime().isBefore(aliveThreshold);
      instances.add(new ServiceInstance(
          serviceName,
          environment,
          node.getNodeIp(),
          node.getNodePort() == null ? 0 : node.getNodePort(),
          available,
          node.getLastRefreshTime() == null ? null : node.getLastRefreshTime().toInstant(ZoneOffset.UTC)));
    }

    discoverCache.put(key, instances);
    return instances;
  }

  @Scheduled(fixedDelayString = "${proxy.registry.offline-scan-interval-seconds:10}000")
  @Transactional
  public void scanAndMarkOffline() {
    LocalDateTime timeoutAt = LocalDateTime.ofInstant(
        Instant.now(clock).minusSeconds(registryProperties.heartbeatTimeoutSeconds()),
        ZoneOffset.UTC);

    List<RcNode> onlineNodes = nodeMapper.selectList(new LambdaQueryWrapper<RcNode>()
        .eq(RcNode::getStatus, "0")
        .isNotNull(RcNode::getLastRefreshTime)
        .lt(RcNode::getLastRefreshTime, timeoutAt));

    if (onlineNodes.isEmpty()) {
      return;
    }

    for (RcNode node : onlineNodes) {
      node.setStatus("1");
      node.setUpdateTime(LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC));
      nodeMapper.updateById(node);
    }

    discoverCache.invalidateAll();
  }

  private static String cacheKey(String serviceName, String environment) {
    return serviceName + "|" + environment;
  }

  @Transactional
  public AgentRegisterResponse registerAgent(AgentRegisterRequest request) {
    if (request == null) {
      throw new BusinessException(400, "request body is required");
    }
    if (request.getAgentIp() == null || request.getAgentIp().isBlank()) {
      throw new BusinessException(400, "agentIp is required");
    }
    if (request.getAgentPort() == null || request.getAgentPort() <= 0 || request.getAgentPort() > 65535) {
      throw new BusinessException(400, "agentPort is invalid");
    }

    AgentRegistry existing = agentRegistryMapper.selectOne(new LambdaQueryWrapper<AgentRegistry>()
        .eq(AgentRegistry::getAgentIp, request.getAgentIp())
        .eq(AgentRegistry::getAgentPort, request.getAgentPort()));

    LocalDateTime now = LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
    
    AgentRegistry result;
    if (existing != null) {
      existing.setNodeName(request.getNodeName());
      existing.setOsType(request.getOsType());
      existing.setAppId(request.getAppId());
      existing.setRemark(request.getRemark());
      existing.setNodeStatus(1);
      existing.setUpdateTime(now);
      agentRegistryMapper.updateById(existing);
      result = existing;
    } else {
      AgentRegistry agentRegistry = new AgentRegistry();
      agentRegistry.setId(UUID.randomUUID().toString().replace("-", ""));
      agentRegistry.setNodeName(request.getNodeName());
      agentRegistry.setOsType(request.getOsType());
      agentRegistry.setAppId(request.getAppId());
      agentRegistry.setAgentIp(request.getAgentIp());
      agentRegistry.setAgentPort(request.getAgentPort());
      agentRegistry.setNodeEnabled(0);
      agentRegistry.setNodeStatus(1);
      agentRegistry.setRemark(request.getRemark());
      agentRegistry.setCreateTime(now);
      agentRegistry.setUpdateTime(now);
      agentRegistryMapper.insert(agentRegistry);
      result = agentRegistry;
    }

    return AgentRegisterResponse.builder()
        .id(result.getId())
        .nodeName(result.getNodeName())
        .osType(result.getOsType())
        .appId(result.getAppId())
        .agentIp(result.getAgentIp())
        .agentPort(result.getAgentPort())
        .nodeEnabled(result.getNodeEnabled())
        .nodeStatus(result.getNodeStatus())
        .remark(result.getRemark())
        .createTime(result.getCreateTime() != null ? result.getCreateTime().format(dateTimeFormatter) : null)
        .updateTime(result.getUpdateTime() != null ? result.getUpdateTime().format(dateTimeFormatter) : null)
        .build();
  }

  @Transactional
  public boolean heartbeatAgent(String agentIp, Integer agentPort) {
    if (agentIp == null || agentIp.isBlank()) {
      throw new BusinessException(400, "agentIp is required");
    }
    if (agentPort == null || agentPort <= 0 || agentPort > 65535) {
      throw new BusinessException(400, "agentPort is invalid");
    }

    AgentRegistry existing = agentRegistryMapper.selectOne(new LambdaQueryWrapper<AgentRegistry>()
        .eq(AgentRegistry::getAgentIp, agentIp)
        .eq(AgentRegistry::getAgentPort, agentPort));

    if (existing != null) {
      existing.setNodeStatus(1);
      existing.setUpdateTime(LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC));
      agentRegistryMapper.updateById(existing);
      return true;
    }
    return false;
  }
}