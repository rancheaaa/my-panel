package com.cq.agent.client;

import com.cq.agent.client.download.DownloadTask;
import com.cq.agent.client.upload.UploadTask;
import com.cq.agent.util.AtomicFileWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class TransferMetaStore<T extends TaskInfo> {

    private static final Logger logger = LoggerFactory.getLogger(TransferMetaStore.class);
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Path metaDir;
    private final Class<T> taskClass;

    public TransferMetaStore(Path metaDir, Class<T> taskClass) throws IOException {
        this.metaDir = metaDir;
        this.taskClass = taskClass;
        
        if (!Files.exists(metaDir)) {
            Files.createDirectories(metaDir);
        }
        
        logger.info("TransferMetaStore 初始化完成: dir={}", metaDir.toAbsolutePath());
    }

    public void saveTask(T task) throws IOException {
        String transferId = getTransferId(task);
        String status = getStatus(task);

        cleanupOldStatusFiles(transferId);

        String json = gson.toJson(task);
        Path metaPath = resolveMetaPathWithStatus(task, status);

        AtomicFileWriter.writeAtomically(metaPath, json);
        logger.debug("任务元数据已保存: transferId={}, status={}, path={}", transferId, status, metaPath);
    }

    public Optional<T> loadTask(String transferId) {
        try {
            Path metaPath = findLatestMetaFile(transferId);

            if (metaPath == null || !Files.exists(metaPath)) {
                return Optional.empty();
            }

            String json = Files.readString(metaPath);
            T task = gson.fromJson(json, taskClass);
            return Optional.ofNullable(task);

        } catch (IOException | JsonSyntaxException e) {
            logger.error("加载任务元数据失败: transferId={}", transferId, e);
            return Optional.empty();
        }
    }

    public void deleteTask(String transferId) throws IOException {
        cleanupOldStatusFiles(transferId);
        logger.debug("已删除任务所有状态元数据: transferId={}", transferId);
    }

    public void moveToFailedQueue(String transferId, Path failedQueueDir) throws IOException {
        if (failedQueueDir == null || !Files.exists(failedQueueDir)) {
            logger.warn("失败队列目录不存在，无法移动文件: dir={}", failedQueueDir);
            return;
        }

        Path latestFile = findLatestMetaFile(transferId);
        if (latestFile == null || !Files.exists(latestFile)) {
            logger.debug("未找到任务元数据文件，无需移动: transferId={}", transferId);
            return;
        }

        Path targetFile = failedQueueDir.resolve(latestFile.getFileName());

        try {
            Files.move(latestFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            logger.info("已将失败任务移动到重试队列: source={}, target={}", latestFile, targetFile);
        } catch (IOException e) {
            logger.error("移动失败任务文件到重试队列失败: source={}, target={}, error={}",
                    latestFile, targetFile, e.getMessage());
            throw e;
        }
    }

    public List<T> recoverPendingTasks() {
        List<T> pendingTasks = new ArrayList<>();
        
        try (Stream<Path> paths = Files.list(metaDir)) {
            paths.filter(Files::isRegularFile)
                  .filter(path -> path.toString().endsWith(".json"))
                  .forEach(path -> {
                      try {
                          String json = Files.readString(path);
                          T task = gson.fromJson(json, taskClass);
                          
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
                          T task = gson.fromJson(json, taskClass);
                          
                          if (isExpired(task, timeoutMs, currentTime)) {
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

    private Path resolveMetaPath(T task) {
        String transferId = getTransferId(task);
        String status = getStatus(task);
        String type = inferTypeFromTask(task);
        return metaDir.resolve(status + "-" + type + "-" + transferId + ".json");
    }

    private Path resolveMetaPathWithStatus(T task, String status) {
        String transferId = getTransferId(task);
        String type = inferTypeFromTask(task);
        return metaDir.resolve(status + "-" + type + "-" + transferId + ".json");
    }

    private void cleanupOldStatusFiles(String transferId) throws IOException {
        String type = inferTypeFromClass();

        try (Stream<Path> paths = Files.list(metaDir)) {
            paths.filter(Files::isRegularFile)
                  .filter(path -> {
                      String fileName = path.getFileName().toString();
                      return fileName.matches(".+-" + type + "-" + transferId + "\\.json$");
                  })
                  .forEach(path -> {
                      try {
                          AtomicFileWriter.deleteIfExists(path);
                          logger.debug("已清理旧状态文件: {}", path.getFileName());
                      } catch (IOException e) {
                          logger.warn("清理旧状态文件失败: {}", path, e);
                      }
                  });
        }
    }

    private Path findLatestMetaFile(String transferId) {
        String type = inferTypeFromClass();
        Path latestPath = null;
        long latestModifiedTime = 0;

        try (Stream<Path> paths = Files.list(metaDir)) {
            List<Path> matchingFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.matches(".+-" + type + "-" + transferId + "\\.json$");
                    })
                    .toList();

            for (Path path : matchingFiles) {
                long modifiedTime = Files.getLastModifiedTime(path).toMillis();
                if (modifiedTime > latestModifiedTime) {
                    latestModifiedTime = modifiedTime;
                    latestPath = path;
                }
            }
        } catch (IOException e) {
            logger.error("查找元数据文件失败: transferId={}", transferId, e);
        }

        if (latestPath != null) {
            logger.debug("找到最新的元数据文件: path={}, lastModified={}", latestPath, latestModifiedTime);
        }

        return latestPath;
    }

    private String inferTypeFromClass() {
        String className = taskClass.getSimpleName().toLowerCase();
        if (className.contains("upload")) {
            return "upload";
        } else if (className.contains("download")) {
            return "download";
        }
        return "unknown";
    }

    private String getTransferId(T task) {
        if (task instanceof UploadTask uploadTask) {
            return uploadTask.getTransferId();
        } else if (task instanceof DownloadTask downloadTask) {
            return downloadTask.getTransferId();
        }
        throw new RuntimeException("不支持的任务类型: " + task.getClass().getName());
    }

    private String getStatus(T task) {
        if (task instanceof UploadTask uploadTask) {
            return uploadTask.getStatus().toString();
        } else if (task instanceof DownloadTask downloadTask) {
            return downloadTask.getStatus().toString();
        }
        throw new RuntimeException("不支持的任务类型: " + task.getClass().getName());
    }

    private String inferTypeFromTask(T task) {
        if (task instanceof UploadTask) {
            return "upload";
        } else if (task instanceof DownloadTask) {
            return "download";
        }
        return "unknown";
    }

    private boolean isCompleted(T task) {
        String statusStr;
        if (task instanceof UploadTask uploadTask) {
            statusStr = uploadTask.getStatus().toString().toUpperCase();
        } else if (task instanceof DownloadTask downloadTask) {
            statusStr = downloadTask.getStatus().toString().toUpperCase();
        } else {
            return false;
        }

        return statusStr.equals("SUCCESS") ||
               statusStr.contains("UPLOAD_SUCCESS") ||
               statusStr.contains("DOWNLOAD_SUCCESS") ||
               statusStr.equals("FAILED");
    }

    private boolean isExpired(T task, long timeoutMs, long currentTime) {
        String updateTimeStr;
        if (task instanceof UploadTask uploadTask) {
            updateTimeStr = uploadTask.getUpdateTime();
        } else if (task instanceof DownloadTask downloadTask) {
            updateTimeStr = downloadTask.getUpdateTime();
        } else {
            return false;
        }

        if (updateTimeStr != null && !updateTimeStr.isEmpty()) {
            try {
                LocalDateTime updateTime = LocalDateTime.parse(updateTimeStr, FORMATTER);
                long taskTime = updateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                return (currentTime - taskTime) > timeoutMs;
            } catch (Exception e) {
                logger.warn("解析更新时间失败: {}", updateTimeStr, e);
                return false;
            }
        }

        return false;
    }

    public boolean existsTaskWithLocalPath(String localFilePath, String targetAgentKey) {
        if (localFilePath == null || metaDir == null || !Files.exists(metaDir)) {
            return false;
        }

        try (Stream<Path> paths = Files.list(metaDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .anyMatch(path -> {
                        try {
                            String json = Files.readString(path);
                            T task = gson.fromJson(json, taskClass);

                            if (task == null) {
                                return false;
                            }

                            String taskLocalPath = getLocalFilePath(task);
                            if (!localFilePath.equals(taskLocalPath)) {
                                return false;
                            }

                            String taskTargetAgent = extractTargetAgentFromTask(task);
                            return targetAgentKey.equals(taskTargetAgent);

                        } catch (Exception e) {
                            logger.warn("解析任务文件失败，跳过: path={}", path, e);
                            return false;
                        }
                    });
        } catch (IOException e) {
            logger.error("查询任务失败: localPath={}, error={}", localFilePath, e.getMessage());
            return false;
        }
    }

    private String getLocalFilePath(T task) {
        if (task instanceof UploadTask uploadTask) {
            return uploadTask.getLocalFilePath();
        } else if (task instanceof DownloadTask downloadTask) {
            return downloadTask.getLocalFilePath();
        }
        return null;
    }

    private String extractTargetAgentFromTask(T task) {
        String remotePath;
        if (task instanceof UploadTask uploadTask) {
            remotePath = uploadTask.getRemoteAgentApiUrl();
        } else if (task instanceof DownloadTask downloadTask) {
            remotePath = downloadTask.getRemoteAgentApiUrl();
        } else {
            return null;
        }

        if (remotePath != null && remotePath.startsWith("http://")) {
            try {
                URI uri = new URI(remotePath);
                return uri.getHost() + ":" + uri.getPort();
            } catch (Exception e) {
                return remotePath;
            }
        }

        return remotePath;
    }
}
