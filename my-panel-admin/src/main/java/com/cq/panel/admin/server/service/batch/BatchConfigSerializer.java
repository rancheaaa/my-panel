package com.cq.panel.admin.server.service.batch;

import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.RetryConfig;
import com.cq.panel.common.dto.batch.ScanConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.TransferConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Component;

/**
 * JSON序列化工具
 * 使用Gson与Agent端保持完全一致的序列化行为，确保MD5一致性
 * 将Admin的BatchTransferTask实体转换为Agent端公共Bean AgentTaskConfig
 */
@Component
public class BatchConfigSerializer {

  /** 与Agent端ConfigFileManager中完全一致的Gson实例 */
  private static final Gson GSON = new GsonBuilder()
      .setPrettyPrinting()
      .create();

  public <T> String serialize(T object) throws SerializationException {
    try {
      return GSON.toJson(object);
    } catch (Exception e) {
      throw new SerializationException("序列化失败: " + e.getMessage(), e);
    }
  }

  /**
   * 将Admin实体转换为公共AgentTaskConfig Bean并序列化为JSON
   * 使用Gson序列化，与Agent端ConfigFileManager.saveTaskConfig()行为完全一致
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
      // 使用Gson序列化，与Agent端完全一致
      return GSON.toJson(config);
    } catch (Exception e) {
      throw new SerializationException("Agent格式序列化失败: " + e.getMessage(), e);
    }
  }

  private List<TargetAgentInfo> buildTargetAgents(BatchTransferTask task) {
    List<String> targetDirs = parseToArray(task.getTargetDirs());
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

  private List<String> parseToArray(String value) {
    if (value == null || value.trim().isEmpty()) {
      return Collections.emptyList();
    }
    return Arrays.stream(value.split(";"))
        .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private List<String> parseJsonArray(String value) {
    if (value == null || value.trim().isEmpty()) {
      return Collections.emptyList();
    }
    String str = value.trim();
    if (str.startsWith("[") && str.endsWith("]")) {
      try {
        return GSON.fromJson(str, List.class);
      } catch (JsonSyntaxException e) {
        return Arrays.stream(str.replace("[", "").replace("]", "").split(","))
            .map(s -> s.trim().replaceAll("^\"|\"$", "")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
      }
    }
    return Collections.singletonList(str);
  }

  /**
   * 将配置文件Map打包为ZIP字节数组
   */
  public byte[] generateConfigZip(Map<String, String> configs) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ZipOutputStream zos = new ZipOutputStream(baos)) {
      for (Map.Entry<String, String> entry : configs.entrySet()) {
        ZipEntry zipEntry = new ZipEntry(entry.getKey());
        zos.putNextEntry(zipEntry);
        zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
      }
      zos.flush();
      return baos.toByteArray();
    } catch (Exception e) {
      throw new SerializationException("生成配置ZIP失败: " + e.getMessage(), e);
    }
  }

  public <T> T deserialize(String json, Class<T> clazz) throws DeserializationException {
    try {
      return GSON.fromJson(json, clazz);
    } catch (Exception e) {
      throw new DeserializationException("反序列化失败: " + e.getMessage(), e);
    }
  }

  @SuppressWarnings("all")
  public static class SerializationException extends RuntimeException {
    public SerializationException(String message) {
      super(message);
    }

    public SerializationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @SuppressWarnings("all")
  public static class DeserializationException extends RuntimeException {
    public DeserializationException(String message) {
      super(message);
    }

    public DeserializationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
