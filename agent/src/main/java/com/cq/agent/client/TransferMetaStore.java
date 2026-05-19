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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final ConcurrentHashMap<String, T> taskMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> localPathIndex = new ConcurrentHashMap<>();

    public TransferMetaStore(Path metaDir, Class<T> taskClass) throws IOException {
        this.metaDir = metaDir;
        this.taskClass = taskClass;

        if (!Files.exists(metaDir)) {
            Files.createDirectories(metaDir);
        }

        loadExistingTasksFromDisk();

        logger.info("TransferMetaStore 初始化完成: dir={}, loadedTasks={}", metaDir.toAbsolutePath(), taskMap.size());
    }

    private void loadExistingTasksFromDisk() {
        if (!Files.exists(metaDir)) {
            return;
        }
        AtomicInteger loadedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        String type = inferTypeFromClass();
        try (Stream<Path> stream = Files.list(metaDir)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")
                            && p.getFileName().toString().contains("-" + type + "-"))
                    .forEach(p -> {
                        try {
                            String json = Files.readString(p);
                            T task = gson.fromJson(json, taskClass);
                            if (task != null) {
                                String transferId = getTransferId(task);
                                putToMemory(task, transferId);
                                loadedCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            logger.warn("加载残留任务元数据失败: {}", p, e);
                            failedCount.incrementAndGet();
                        }
                    });
        } catch (IOException e) {
            logger.warn("扫描meta目录加载任务失败: {}", metaDir, e);
        }
        if (loadedCount.get() > 0 || failedCount.get() > 0) {
            logger.info("从磁盘加载任务元数据到内存完成: loaded={}, failed={}", loadedCount.get(), failedCount.get());
        }
    }

    private void putToMemory(T task, String transferId) {
        if (task == null || transferId == null) return;
        T existing = taskMap.put(transferId, task);
        if (existing != null) {
            removeLocalPathIndex(existing);
        }
        buildLocalPathIndex(task, transferId);
    }

    private void removeFromMemory(String transferId) {
        if (transferId == null) return;
        T removed = taskMap.remove(transferId);
        if (removed != null) {
            removeLocalPathIndex(removed);
        }
    }

    private void buildLocalPathIndex(T task, String transferId) {
        String localPath = getLocalFilePath(task);
        String targetAgent = extractTargetAgentFromTask(task);
        if (localPath != null && targetAgent != null) {
            String indexKey = localPath + "|" + targetAgent;
            localPathIndex.put(indexKey, transferId);
        }
    }

    private void removeLocalPathIndex(T task) {
        String localPath = getLocalFilePath(task);
        String targetAgent = extractTargetAgentFromTask(task);
        if (localPath != null && targetAgent != null) {
            String indexKey = localPath + "|" + targetAgent;
            localPathIndex.remove(indexKey);
        }
    }

    public void saveTask(T task) throws IOException {
        String transferId = getTransferId(task);
        String status = getStatus(task);

        cleanupOldStatusFiles(transferId);

        String json = gson.toJson(task);
        Path metaPath = resolveMetaPathWithStatus(task, status);

        AtomicFileWriter.writeAtomically(metaPath, json);
        putToMemory(task, transferId);

        logger.debug("任务元数据已保存: transferId={}, status={}, path={}", transferId, status, metaPath);
    }

    public Optional<T> loadTask(String transferId) {
        if (transferId == null) {
            return Optional.empty();
        }
        T cached = taskMap.get(transferId);
        if (cached == null) {
            return Optional.empty();
        }
        return Optional.of(cached);
    }

    public void deleteTask(String transferId) throws IOException {
        cleanupOldStatusFiles(transferId);
        removeFromMemory(transferId);
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

    public void moveToSuccessQueue(String transferId, Path successQueueDir) throws IOException {
        if (successQueueDir == null) {
            logger.debug("成功队列目录未配置，跳过移动: transferId={}", transferId);
            return;
        }

        if (!Files.exists(successQueueDir)) {
            try {
                Files.createDirectories(successQueueDir);
                logger.info("已创建成功队列目录: dir={}", successQueueDir.toAbsolutePath());
            } catch (IOException e) {
                logger.error("创建成功队列目录失败: dir={}, error={}", successQueueDir, e.getMessage());
                return;
            }
        }

        Path latestFile = findLatestMetaFile(transferId);
        if (latestFile == null || !Files.exists(latestFile)) {
            logger.debug("未找到任务元数据文件，无需移动: transferId={}", transferId);
            return;
        }

        Path targetFile = successQueueDir.resolve(latestFile.getFileName());

        try {
            Files.move(latestFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            logger.info("已将成功任务移动到成功队列: source={}, target={}", latestFile, targetFile);
        } catch (IOException e) {
            logger.error("移动成功任务文件到成功队列失败: source={}, target={}, error={}",
                    latestFile, targetFile, e.getMessage());
            throw e;
        }
    }

    public List<T> recoverPendingTasks() {
        List<T> pendingTasks = new ArrayList<>();
        for (T task : taskMap.values()) {
            if (!isCompleted(task)) {
                pendingTasks.add(task);
            }
        }
        logger.info("恢复待处理任务数: count={} (from memory)", pendingTasks.size());
        return pendingTasks;
    }

    public void cleanupExpiredTasks(long timeoutMs) {
        long currentTime = System.currentTimeMillis();
        List<String> expiredIds = new ArrayList<>();
        for (var entry : taskMap.entrySet()) {
            T task = entry.getValue();
            if (isExpired(task, timeoutMs, currentTime)) {
                expiredIds.add(entry.getKey());
            }
        }
        for (String transferId : expiredIds) {
            try {
                cleanupOldStatusFiles(transferId);
                removeFromMemory(transferId);
                logger.debug("清理过期任务: transferId={}", transferId);
            } catch (IOException e) {
                logger.warn("清理过期任务失败: transferId={}", transferId, e);
            }
        }
    }

    public boolean existsTaskWithLocalPath(String localFilePath, String targetAgentKey) {
        if (localFilePath == null || targetAgentKey == null) {
            return false;
        }
        String indexKey = localFilePath + "|" + targetAgentKey;
        String transferId = localPathIndex.get(indexKey);
        return transferId != null && taskMap.containsKey(transferId);
    }

    public int getMemorySize() {
        return taskMap.size();
    }

    public int getLocalPathIndexSize() {
        return localPathIndex.size();
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
