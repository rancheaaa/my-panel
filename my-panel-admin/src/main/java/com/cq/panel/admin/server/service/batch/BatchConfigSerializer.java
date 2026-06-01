package com.cq.panel.admin.server.service.batch;

import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.RetryConfig;
import com.cq.panel.common.dto.batch.ScanConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.TransferConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * JSON序列化工具
 * 将Admin的BatchTransferTask实体转换为Agent端公共Bean AgentTaskConfig
 * 确保字段数量和格式与spec.md设计一致
 */
@Component
public class BatchConfigSerializer {

  private final ObjectMapper objectMapper;

  public BatchConfigSerializer() {
    this.objectMapper = createObjectMapper();
  }

  public BatchConfigSerializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public <T> String serialize(T object) throws SerializationException {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new SerializationException("序列化失败: " + e.getMessage(), e);
    }
  }

  /**
   * 将Admin实体转换为公共AgentTaskConfig Bean并序列化为JSON
   * 确保Admin写入的字段数量和格式与Agent使用的一致，符合spec.md设计
   */
  public String serializeForAgent(BatchTransferTask task) throws SerializationException {
    try {
      AgentTaskConfig config = new AgentTaskConfig();
      config.setTaskId(task.getId());
      config.setTaskName(task.getTaskName());
      config.setTaskDescription(task.getTaskDescription());
      config.setStatus(task.getStatus());
      config.setVersion(new SimpleDateFormat("yyyyMMddHHmmss").format(task.getUpdateTime()));
      config.setSourceAgentId(task.getSourceAgentId());
      config.setSourceAgentName(task.getSourceAgentName());
      config.setSourceDir(task.getSourceDir());
      config.setTargetAgents(buildTargetAgents(task));
      config.setIncludePatterns(parseJsonArray(task.getIncludePatterns()));
      config.setExcludePatterns(parseJsonArray(task.getExcludePatterns()));
      config.setScanConfig(buildScanConfig(task));
      config.setTransferConfig(buildTransferConfig(task));
      config.setRetryConfig(buildRetryConfig(task));
      config.setTaskPriority(task.getTaskPriority());
      if (task.getStartedAt() != null) {
        config.setStartedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(task.getStartedAt()));
      }
      return objectMapper.writeValueAsString(config);
    } catch (JsonProcessingException e) {
      throw new SerializationException("Agent格式序列化失败: " + e.getMessage(), e);
    }
  }

  private List<TargetAgentInfo> buildTargetAgents(BatchTransferTask task) {
    List<String> targetDirs = parseToArray(task.getTargetDirs(), ";");
    List<String> agentIds = parseJsonArray(task.getTargetAgentIds());
    List<String> agentNames = parseJsonArray(task.getTargetAgentNames());
    int size = Math.max(targetDirs.size(), Math.max(agentIds.size(), agentNames.size()));
    List<TargetAgentInfo> agents = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      TargetAgentInfo info = new TargetAgentInfo();
      info.setAgentId(i < agentIds.size() ? agentIds.get(i) : "");
      info.setAgentName(i < agentNames.size() ? agentNames.get(i) : "");
      info.setTargetDir(i < targetDirs.size() ? targetDirs.get(i) : "");
      agents.add(info);
    }
    return agents;
  }

  private ScanConfig buildScanConfig(BatchTransferTask task) {
    ScanConfig scanConfig = new ScanConfig();
    scanConfig.setCronExpression(task.getScanCronExpression());
    scanConfig.setMaxScanFiles(task.getMaxScanFiles());
    scanConfig.setScheduledEnabled(task.getScheduledEnabled() != null && task.getScheduledEnabled() == 1);
    scanConfig.setScheduledStartTime(task.getScheduledStartTime());
    scanConfig.setScheduledEndTime(task.getScheduledEndTime());
    return scanConfig;
  }

  private TransferConfig buildTransferConfig(BatchTransferTask task) {
    TransferConfig transferConfig = new TransferConfig();

    // 1. 基础设置
    transferConfig.setTransferMode(task.getTransferMode());
    transferConfig
        .setPreserveDirStructure(task.getPreserveDirStructure() != null && task.getPreserveDirStructure() == 1);

    // 2. 路由控制
    transferConfig.setRoutingStrategy(task.getRoutingStrategy());
    transferConfig.setRoutingConfig(task.getRoutingConfig());

    // 3. 性能控制（暂无字段，保留扩展）
    // transferConfig.setMaxBandwidthKbS(...);

    // 4. 传输后操作
    transferConfig.setPostTransferAction(task.getPostTransferAction());
    transferConfig.setBackupDir(task.getBackupDir());
    transferConfig.setBackupMode(task.getBackupMode());

    return transferConfig;
  }

  private RetryConfig buildRetryConfig(BatchTransferTask task) {
    RetryConfig retryConfig = new RetryConfig();
    retryConfig.setEnabled(task.getRetryEnabled() != null && task.getRetryEnabled() == 1);
    retryConfig.setMaxDays(task.getRetryMaxDays());
    retryConfig.setIntervalMin(task.getRetryIntervalMin());
    retryConfig.setMaxRetryCount(task.getMaxRetryCount());
    retryConfig.setBackoffType(task.getRetryBackoffType());
    return retryConfig;
  }

  private List<String> parseToArray(String value, String delimiter) {
    if (value == null || value.trim().isEmpty()) {
      return Collections.emptyList();
    }
    return Arrays.asList(value.split(delimiter)).stream()
        .map(s -> s.trim()).filter(s -> !s.isEmpty()).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private List<String> parseJsonArray(String value) {
    if (value == null || value.trim().isEmpty()) {
      return Collections.emptyList();
    }
    String str = value.trim();
    if (str.startsWith("[") && str.endsWith("]")) {
      try {
        return objectMapper.readValue(str, List.class);
      } catch (Exception e) {
        return Arrays.asList(str.replace("[", "").replace("]", "").split(",")).stream()
            .map(s -> s.trim().replaceAll("^\"|\"$", "")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
      }
    }
    return Collections.singletonList(str);
  }

  public <T> T deserialize(String json, Class<T> clazz) throws DeserializationException {
    try {
      return objectMapper.readValue(json, clazz);
    } catch (IOException e) {
      throw new DeserializationException("反序列化失败: " + e.getMessage(), e);
    }
  }

  private static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper;
  }

  public static class SerializationException extends RuntimeException {
    public SerializationException(String message) {
      super(message);
    }

    public SerializationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class DeserializationException extends RuntimeException {
    public DeserializationException(String message) {
      super(message);
    }

    public DeserializationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
