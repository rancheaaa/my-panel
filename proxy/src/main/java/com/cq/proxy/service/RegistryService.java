package com.cq.proxy.service;

import com.cq.proxy.dto.AgentRegisterRequest;
import com.cq.proxy.dto.AgentRegisterResponse;
import com.cq.proxy.dto.ServiceInstance;
import com.cq.proxy.dto.ServiceRegisterRequest;
import com.cq.proxy.config.ProxyRegistryProperties;
import com.cq.proxy.exception.BusinessException;
import com.cq.proxy.repository.entity.AgentRegistry;
import com.cq.proxy.repository.entity.RcNode;
import com.cq.proxy.repository.mapper.AgentRegistryMapper;
import com.cq.proxy.repository.mapper.RcNodeMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistryService {

    private final Logger logger = LoggerFactory.getLogger(RegistryService.class);

    private final RcDictionaryService dictionaryService;
    private final RcNodeMapper nodeMapper;
    private final AgentRegistryMapper agentRegistryMapper;
    private final ProxyRegistryProperties registryProperties;
    private final Cache<String, List<ServiceInstance>> discoverCache;
    private final DateTimeFormatter dateTimeFormatter;

    public RegistryService(
            RcDictionaryService dictionaryService,
            RcNodeMapper nodeMapper,
            AgentRegistryMapper agentRegistryMapper,
            ProxyRegistryProperties registryProperties,
            Caffeine<Object, Object> caffeine
    ) {
        this.dictionaryService = dictionaryService;
        this.nodeMapper = nodeMapper;
        this.agentRegistryMapper = agentRegistryMapper;
        this.registryProperties = registryProperties;
        this.discoverCache = caffeine.build();
        this.dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }


    @Transactional
    public void heartbeat(ServiceRegisterRequest request) {
        if (request == null) {
            throw new BusinessException(400, "request body is required");
        }
        if (request.host() == null || request.host().isBlank()) {
            throw new BusinessException(400, "host is required");
        }
        if (request.port() <= 0 || request.port() > 65535) {
            throw new BusinessException(400, "port is invalid");
        }
        if ("localhost".equals(request.host())  || "127.0.0.1".equals(request.host())) {
            throw new BusinessException(400, "host is required");
        }

        long envId = dictionaryService.getOrCreateEnvId(request.environment());
        long projectId = dictionaryService.getOrCreateProjectId(request.serviceName());

        List<RcNode> existingNodes = nodeMapper.selectByEnvProjectIpPort(envId, projectId, request.host(), request.port());

        LocalDateTime now = LocalDateTime.now();
        if (!existingNodes.isEmpty()) {
            for (RcNode existingNode : existingNodes) {
                existingNode.setLastRefreshTime(now);
                existingNode.setUpdateTime(now);
                nodeMapper.updateById(existingNode);
            }
        }
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
        if ("localhost".equals(request.host())  || "127.0.0.1".equals(request.host())) {
            throw new BusinessException(400, "host is required");
        }

        long envId = dictionaryService.getOrCreateEnvId(request.environment());
        long projectId = dictionaryService.getOrCreateProjectId(request.serviceName());

        List<RcNode> existingNodes = nodeMapper.selectByEnvProjectIpPort(envId, projectId, request.host(), request.port());

        LocalDateTime now = LocalDateTime.now();
        if (existingNodes.isEmpty()) {
            RcNode node = new RcNode();
            node.setEnvId(envId);
            node.setProjectId(projectId);
            node.setNodeIp(request.host());
            node.setNodePort(request.port());
            node.setStatus("0");
            // 设置zone字段，从请求中获取或使用默认值
            String zone = request.zone() != null && !request.zone().isBlank() ? request.zone() : "default";
            node.setZone(zone);
            node.setLastRefreshTime(now);
            node.setCreateTime(now);
            node.setUpdateTime(now);
            nodeMapper.insert(node);
        } else {
            for (RcNode existingNode : existingNodes) {
                existingNode.setStatus("0");
                // 更新zone字段，如果请求中有zone值则更新
                if (request.zone() != null && !request.zone().isBlank()) {
                    existingNode.setZone(request.zone());
                }
                existingNode.setLastRefreshTime(now);
                existingNode.setUpdateTime(now);
                nodeMapper.updateById(existingNode);
            }
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
        LocalDateTime aliveThreshold = LocalDateTime.now().minusSeconds(registryProperties.getHeartbeatTimeoutSeconds());

        List<RcNode> nodes = nodeMapper.selectByEnvProjectStatus(envId, projectId, "0");

        List<ServiceInstance> instances = new ArrayList<>();
        for (RcNode node : nodes) {
            boolean available = node.getLastRefreshTime() != null && !node.getLastRefreshTime().isBefore(aliveThreshold);
            instances.add(new ServiceInstance(
                    serviceName,
                    environment,
                    node.getNodeIp(),
                    node.getNodePort() == null ? 0 : node.getNodePort(),
                    available,
                    node.getLastRefreshTime() == null ? null : dateTimeFormatter.format(node.getLastRefreshTime()),
                    node.getZone()));
        }

        discoverCache.put(key, instances);
        return instances;
    }

    @Scheduled(fixedDelayString = "${proxy.registry.offline-scan-interval-seconds:10}000")
    @Transactional
    public void scanAndMarkOffline() {
        LocalDateTime timeoutAt = LocalDateTime.now().minusSeconds(registryProperties.getHeartbeatTimeoutSeconds());

        List<RcNode> onlineNodes = nodeMapper.selectByStatus("0");

        if (onlineNodes.isEmpty()) {
            return;
        }

        for (RcNode node : onlineNodes) {
            if (node.getLastRefreshTime() != null && node.getLastRefreshTime().isBefore(timeoutAt)) {
                node.setStatus("1");
                node.setUpdateTime(LocalDateTime.now());
                nodeMapper.updateById(node);
                logger.debug("Mark {} as offline", node);
            }
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

        AgentRegistry existing = agentRegistryMapper.selectByIpPort(request.getAgentIp(), request.getAgentPort());

        LocalDateTime now = LocalDateTime.now();

        AgentRegistry result;
        if (existing != null) {
            existing.setNodeName(request.getNodeName());
            existing.setOsType(request.getOsType());
            existing.setAppId(request.getAppId());
            existing.setNodeStatus(request.getNodeStatus());
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
            agentRegistry.setNodeStatus(request.getNodeStatus());
            agentRegistry.setNodeEnabled(0);
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

        AgentRegistry existing = agentRegistryMapper.selectByIpPort(agentIp, agentPort);

        if (existing != null) {
            existing.setNodeStatus(1);
            existing.setUpdateTime(LocalDateTime.now());
            agentRegistryMapper.updateById(existing);
            return true;
        }
        return false;
    }
}