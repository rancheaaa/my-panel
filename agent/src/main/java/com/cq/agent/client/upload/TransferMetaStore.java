package com.cq.agent.client.upload;

import com.cq.agent.util.AtomicFileWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class TransferMetaStore<TASK> {

    private static final Logger logger = LoggerFactory.getLogger(TransferMetaStore.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Path metaDir;
    private final Class<TASK> taskClass;

    public TransferMetaStore(Path metaDir, Class<TASK> taskClass) throws IOException {
        this.metaDir = metaDir;
        this.taskClass = taskClass;
        
        if (!Files.exists(metaDir)) {
            Files.createDirectories(metaDir);
        }
        
        logger.info("TransferMetaStore 初始化完成: dir={}", metaDir.toAbsolutePath());
    }

    public void saveTask(TASK task) throws IOException {
        String json = gson.toJson(task);
        Path metaPath = resolveMetaPath(task);
        
        AtomicFileWriter.writeAtomically(metaPath, json);
        logger.debug("任务元数据已保存: transferId={}, path={}", getTransferId(task), metaPath);
    }

    public Optional<TASK> loadTask(String transferId) {
        try {
            Path metaPath = resolveMetaPathByType(transferId, "upload");
            if (!Files.exists(metaPath)) {
                metaPath = resolveMetaPathByType(transferId, "download");
            }
            
            if (!Files.exists(metaPath)) {
                return Optional.empty();
            }
            
            String json = Files.readString(metaPath);
            TASK task = gson.fromJson(json, taskClass);
            return Optional.ofNullable(task);
            
        } catch (IOException | JsonSyntaxException e) {
            logger.error("加载任务元数据失败: transferId={}", transferId, e);
            return Optional.empty();
        }
    }

    public void deleteTask(String transferId) throws IOException {
        Path uploadPath = resolveMetaPathByType(transferId, "upload");
        Path downloadPath = resolveMetaPathByType(transferId, "download");
        
        if (Files.exists(uploadPath)) {
            AtomicFileWriter.deleteIfExists(uploadPath);
            logger.debug("已删除上传任务元数据: transferId={}", transferId);
        }
        
        if (Files.exists(downloadPath)) {
            AtomicFileWriter.deleteIfExists(downloadPath);
            logger.debug("已删除下载任务元数据: transferId={}", transferId);
        }
    }

    public List<TASK> recoverPendingTasks() {
        List<TASK> pendingTasks = new ArrayList<>();
        
        try (Stream<Path> paths = Files.list(metaDir)) {
            paths.filter(Files::isRegularFile)
                  .filter(path -> path.toString().endsWith(".json"))
                  .forEach(path -> {
                      try {
                          String json = Files.readString(path);
                          TASK task = gson.fromJson(json, taskClass);
                          
                          if (task != null && !isCompleted(task)) {
                              pendingTasks.add(task);
                          }
                      } catch (IOException | JsonSyntaxException e) {
                          logger.warn("恢复任务失败，跳过损坏的文件: path={}", path, e);
                      }
                  });
        } catch (IOException e) {
            logger.error("扫描元数据目录失败: dir={}", metaDir, e);
        }
        
        logger.info("恢复待处理任务数: count={}", pendingTasks.size());
        return pendingTasks;
    }

    public void cleanupExpiredTasks(long timeoutMs) {
        long currentTime = System.currentTimeMillis();
        
        try (Stream<Path> paths = Files.list(metaDir)) {
            paths.filter(Files::isRegularFile)
                  .filter(path -> path.toString().endsWith(".json"))
                  .forEach(path -> {
                      try {
                          String json = Files.readString(path);
                          TASK task = gson.fromJson(json, taskClass);
                          
                          if (task != null && isExpired(task, timeoutMs, currentTime)) {
                              AtomicFileWriter.deleteIfExists(path);
                              logger.debug("清理过期任务: path={}", path);
                          }
                      } catch (IOException | JsonSyntaxException e) {
                          logger.warn("检查任务过期状态失败，删除文件: path={}", path, e);
                          try {
                              AtomicFileWriter.deleteIfExists(path);
                          } catch (IOException ignored) {}
                      }
                  });
        } catch (IOException e) {
            logger.error("清理过期任务失败: dir={}", metaDir, e);
        }
    }

    private Path resolveMetaPath(TASK task) {
        String transferId = getTransferId(task);
        String type = inferTypeFromTask(task);
        return resolveMetaPathByType(transferId, type);
    }

    private Path resolveMetaPathByType(String transferId, String type) {
        return metaDir.resolve(type + "-" + transferId + ".json");
    }

    private String getTransferId(TASK task) {
        try {
            var method = task.getClass().getMethod("getTransferId");
            return (String) method.invoke(task);
        } catch (Exception e) {
            throw new RuntimeException("无法获取 transferId", e);
        }
    }

    private String inferTypeFromTask(TASK task) {
        String className = task.getClass().getSimpleName().toLowerCase();
        if (className.contains("upload")) {
            return "upload";
        } else if (className.contains("download")) {
            return "download";
        }
        return "unknown";
    }

    private boolean isCompleted(TASK task) {
        try {
            var getStatusMethod = task.getClass().getMethod("getStatus");
            Object status = getStatusMethod.invoke(task);
            String statusStr = status.toString().toUpperCase();

            return statusStr.equals("SUCCESS") ||
                   statusStr.contains("UPLOAD_SUCCESS") ||
                   statusStr.contains("DOWNLOAD_SUCCESS") ||
                   statusStr.equals("FAILED");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isExpired(TASK task, long timeoutMs, long currentTime) {
        try {
            var getUpdateTimeMethod = task.getClass().getMethod("getUpdateTime");
            Object updateTimeObj = getUpdateTimeMethod.invoke(task);
            
            if (updateTimeObj instanceof String) {
                LocalDateTime updateTime = LocalDateTime.parse((String) updateTimeObj, FORMATTER);
                long taskTime = updateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                return (currentTime - taskTime) > timeoutMs;
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
