package com.cq.agent.client.upload;

import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.scheduler.BatchUploadListener;
import com.cq.agent.client.BaseAgentClient;
import com.cq.agent.client.RemoteAgentInfo;
import com.cq.agent.client.Util;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.dto.*;
import com.google.gson.reflect.TypeToken;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent upload client for chunked file transfer.
 * Uses memory queue and JSON file-based persistence for durability.
 * 
 * Implements UploadService interface for decorator pattern support.
 */
public class AgentUploader extends BaseAgentClient<UploadTask, UploadListener> implements UploadService {

    private static final Logger logger = LoggerFactory.getLogger(AgentUploader.class);

    private final AgentConfig agentConfig;

    /** 全局ProgressReporter实例（用于重启恢复场景） */
    private ProgressReporter globalProgressReporter;

    public AgentUploader(AgentConfig agentConfig) {
        super(agentConfig, agentConfig.getUploadConcurrentUploads(),
                agentConfig.getUploadMaxQueueDepth(), agentConfig.getUploadWorkerCount(),
                agentConfig.getUploadMaxRetries(), agentConfig.getUploadRetryDelayMs(),
                agentConfig.getUploadConnectTimeoutSeconds(), agentConfig.getUploadRequestTimeoutSeconds(),
                agentConfig.getUploadSendingQueueDir(),
                agentConfig.getMaxUploadRateKBPerSecond(), UploadTask.class, "upload",
                agentConfig.getUploadFailRetryQueueDir());
        this.agentConfig = agentConfig;
    }

    /**
     * 获取Agent配置信息（UploadService接口实现）
     * @return 配置对象
     */
    @Override
    public AgentConfig getAgentConfig() {
        return agentConfig;
    }

    /**
     * 设置全局ProgressReporter（用于重启恢复场景）
     * 应在AgentApplication启动时调用一次，避免每次恢复都新建实例
     *
     * @param progressReporter 全局唯一的进度上报器
     */
    public void setGlobalProgressReporter(com.cq.agent.batch.report.ProgressReporter progressReporter) {
        this.globalProgressReporter = progressReporter;
        logger.info("✅ 已设置全局ProgressReporter: {}", 
            progressReporter != null ? "已配置" : "null");
    }

    @Override
    protected void processTask(UploadTask task) {
        String taskKey = getTaskKey(task);
        String traceId = task.getTraceId();

        try {
            task.setScannedStartTime(Util.currentTime());
            updateTaskStatus(task, UploadTaskStatus.SCANNED);
            task.incrementRetryCount();

            String listenerClassName = task.getListenerClassName();
            if (listenerClassName != null && !listenerCache.containsKey(taskKey)) {
                UploadListener listener = createListenerInstance(listenerClassName, UploadListener.class);
                if (listener != null) {
                    // 尝试恢复监听器状态（用于重启恢复场景）
                    restoreListenerState(listener, task);
                    listenerCache.put(taskKey, listener);
                }
            }

            String fileCheck = Util.checkLocalFileReadable(task.getLocalFilePath());
            if (fileCheck != null) {
                throw new IOException(fileCheck);
            }
            task.setScannedEndTime(Util.currentTime());

            File file = new File(task.getLocalFilePath());

            task.setStatus(UploadTaskStatus.INIT_UPLOADING);
            task.setInitUploadStartTime(Util.currentTime());
            updateTaskStatus(task, UploadTaskStatus.INIT_UPLOADING);

            String transferId = task.getTransferId();
            int chunkSize;
            int totalChunks;
            List<Integer> missingChunks;
            logger.debug("[traceId={}] Get upload session status with transferId: {}", traceId, transferId);
            ApiResponse<ChunkStatusResponse> resp = getUploadStatus(task.getRemoteAgentApiUrl(), transferId, traceId);
            if(resp.getData() == null) {
                ApiResponse<ChunkInitResponse> chunkInitResponse = initUpload(task);
                logger.debug("[traceId={}] Upload initialized: transferId={}, totalChunks={}, chunkSize={}",
                        traceId, chunkInitResponse.getData().getTransferId(), chunkInitResponse.getData().getTotalChunks(), chunkInitResponse.getData().getChunkSize());
                chunkSize = chunkInitResponse.getData().getChunkSize();
                totalChunks = chunkInitResponse.getData().getTotalChunks();
                missingChunks = chunkInitResponse.getData().getMissingChunks();
            } else {
                chunkSize = resp.getData().getChunkSize();
                totalChunks = resp.getData().getTotalChunks();
                missingChunks = resp.getData().getMissingChunks();
            }

            task.setChunkSize(chunkSize);
            task.setTotalChunks(totalChunks);
            task.setMissingChunks(missingChunks);
            task.setInitUploadEndTime(Util.currentTime());
            updateTaskStatus(task, UploadTaskStatus.INIT_UPLOAD_COMPLETED);

            try {
                task.setUploadChunksStartTime(Util.currentTime());
                updateTaskStatus(task, UploadTaskStatus.UPLOADING_CHUNKS);
                uploadChunks(task, file, taskKey, task.getLocalFilePath(), task.getRemoteTargetPath(), traceId);

                ApiResponse<ChunkStatusResponse> finalStatus = fetchLatestStatus(task.getRemoteAgentApiUrl(), task.getTransferId(), traceId);
                if (finalStatus.getData() != null) {
                    final List<Integer> finalMissingChunks = finalStatus.getData().getMissingChunks();
                    task.setMissingChunks(new ArrayList<>(finalMissingChunks));
                    if (!finalMissingChunks.isEmpty()) {
                        throw new IllegalStateException("Upload failed, missing chunks: " + finalMissingChunks);
                    }
                }
                task.setUploadChunksEndTime(Util.currentTime());
                updateTaskStatus(task, UploadTaskStatus.UPLOAD_CHUNKS_COMPLETED);
            } catch (IOException e) {
                ApiResponse<ChunkStatusResponse> latestStatus = fetchLatestStatus(task.getRemoteAgentApiUrl(), task.getTransferId(), traceId);
                logger.error("[traceId={}] Chunk upload failed, status: {} {}", traceId, latestStatus, e.getMessage());
                throw e;
            }

            try {
                task.setMergeChunksStartTime(Util.currentTime());
                updateTaskStatus(task, UploadTaskStatus.MERGING_CHUNKS);
                mergeChunks(task.getRemoteAgentApiUrl(), task.getTransferId(), traceId);
                task.setMergeChunksEndTime(Util.currentTime());
                updateTaskStatus(task, UploadTaskStatus.MERGE_CHUNKS_COMPLETED);
            } catch (IOException e) {
                ApiResponse<ChunkStatusResponse> latestStatus = fetchLatestStatus(task.getRemoteAgentApiUrl(), task.getTransferId(), traceId);
                logger.error("[traceId={}] Merge failed, status:{}  {}", traceId, latestStatus, e.getMessage());
                throw e;
            }

            task.setVerifyStartTime(Util.currentTime());
            updateTaskStatus(task, UploadTaskStatus.VERIFYING_CHUNKS);
            if (verifyRemoteFileExists(task.getRemoteAgentApiUrl(), task.getRemoteTargetPath(), traceId)) {
                task.setVerifyEndTime(Util.currentTime());
                updateTaskStatus(task, UploadTaskStatus.VERIFY_CHUNKS_COMPLETED);
                task.setUploadSuccessTime(Util.currentTime());
                updateTaskStatus(task, UploadTaskStatus.UPLOAD_SUCCESS);
                handleListenerSuccess(taskKey, task);
                inflightTasks.remove(task.getTransferId());
            } else {
                logger.error("[traceId={}] Verify remote file {} failed", traceId, task.getRemoteTargetPath());
                throw new IOException("Upload failed: remote file not found");
            }
            logger.info("[traceId={}] Upload task completed: {}", traceId, taskKey);
        } catch (Exception e) {
            logger.error("[traceId={}] Upload task failed: {} - {}", traceId, taskKey, e.getMessage(), e);
            task.setExceptionDesc(e.getMessage());
            updateTaskStatus(task, UploadTaskStatus.FAILED);

            if (metaStore != null && failedQueueDir != null) {
                try {
                    metaStore.moveToFailedQueue(task.getTransferId(), failedQueueDir);
                } catch (IOException ex) {
                    logger.warn("[traceId={}] 移动失败任务到重试队列失败: {}", traceId, ex.getMessage());
                }
            }

            handleListenerError(taskKey, e.getMessage());
            inflightTasks.remove(task.getTransferId());
        } finally {
            listenerCache.remove(taskKey);
        }
    }

    public void updateTaskStatus(UploadTask task, UploadTaskStatus status) {
        try {
            task.setStatus(status);
            task.updateTimestamp();
        } catch (Exception e) {
            logger.error("更新任务状态失败: {}", e.getMessage());
        }

        inflightTasks.put(getTaskKey(task), task);

        if (metaStore != null) {
            try {
                metaStore.saveTask(task);
            } catch (IOException e) {
                logger.warn("保存任务元数据失败: {}", e.getMessage());
            }
        }
    }

    @Override
    protected String getTaskKey(UploadTask task) {
        return Util.md5(task.getLocalFilePath() + ":" + task.getRemoteTargetPath());
    }

    @Override
    protected void onListenerProgress(UploadListener listener, int total, int processed, double progress) {
        listener.onProgress(total, processed, progress);
    }

    @Override
    protected void onListenerBeforeSend(UploadListener listener, UploadTask task) {
        listener.onBeforeSend(task);
    }

    @Override
    protected void onListenerSuccess(UploadListener listener, UploadTask result) {
        listener.onComplete(result);
    }

    @Override
    protected void onListenerError(UploadListener listener, String message) {
        listener.onError(message);
    }

    public boolean uploadFile(String localFilePath, String remoteTargetInfo, UploadListener listener) {
        String traceId = java.util.UUID.randomUUID().toString().replace("-", "");

        try {
            if (taskQueue.size() > maxQueueDepth) {
                handleListenerError(listener, "Download queue depth is reached: " + maxQueueDepth);
                return false;
            }
            if (localFilePath == null || localFilePath.isBlank()) {
                handleListenerError(listener, "localFilePath must not be null or blank");
                return false;
            }
            // remoteTargetPath like 192.168.1.100:7777@root:/tmp/upload
            // ip:port@username:destFilePath
            if (remoteTargetInfo == null || remoteTargetInfo.isBlank()) {
                handleListenerError(listener, "remoteTargetPath must not be null or blank");
                return false;
            }

            RemoteAgentInfo remoteAgentInfo;
            try {
                remoteAgentInfo = Util.resolveRemoteAgentInfo(remoteTargetInfo);
            } catch (Exception e) {
                handleListenerError(listener, e.getMessage());
                return false;
            }

            if (!new File(localFilePath).isAbsolute()) {
                handleListenerError(listener, "localFilePath must be an absolute path: " + localFilePath);
                return false;
            }

            String fileCheck = Util.checkLocalFileReadable(localFilePath);
            if (fileCheck != null) {
                logger.error("[traceId={}] Local file validation failed: {}", traceId, fileCheck);
                handleListenerError(listener, fileCheck);
                return false;
            }

            File localFile = new File(localFilePath);
            long fileSize = localFile.length();
            long maxFileSize = agentConfig.getMaxFileSize();
            if (maxFileSize > 0 && fileSize > maxFileSize) {
                String errorMsg = "File size exceeds maximum allowed size: " + maxFileSize + "bytes";
                logger.error("[traceId={}] {}", traceId, errorMsg);
                handleListenerError(listener, errorMsg);
                return false;
            }

            logger.debug("[traceId={}] Local file validated: path={}, size={} bytes", traceId, localFilePath, fileSize);

            String taskKey = Util.md5(localFilePath + ":" + remoteAgentInfo.getDestFilePath());

            if (listener != null) {
                listenerCache.put(taskKey, listener);
            }
            UploadTask task = new UploadTask(localFilePath, remoteAgentInfo.getDestFilePath(), fileSize,  "http://" + remoteAgentInfo.getIp() + ":" + remoteAgentInfo.getPort() + "/", remoteAgentInfo.getUsername());
            task.setTransferId(UUID.randomUUID().toString().replace("-", ""));
            task.setTraceId(traceId);
            task.setEnqueuedTime(Util.currentTime());
            task.setListenerClassName(listener != null ? listener.getClass().getName() : null);
            task.updateTimestamp();

            // ★ 设置监听器状态恢复字段（用于重启恢复场景）
            populateRestoreFields(task, listener);

            if (metaStore != null) {
                metaStore.saveTask(task);
            }

            if (!this.inflightTasks.containsKey(task.getTransferId())) {
                this.inflightTasks.put(task.getTransferId(), task);
            }
            return taskQueue.offer(task);
        } catch (Exception e) {
            logger.error("upload file with error!", e);
            handleListenerError(listener, "upload file with error!");
            return false;
        }
    }

    private void uploadChunks(UploadTask task, File file, String taskKey, String localPath, String remotePath, String traceId) throws IOException {
        List<Integer> missingChunks = task.getMissingChunks();
        if (missingChunks.isEmpty()) {
            logger.debug("No missing chunks to upload for task: {}", taskKey);
            return;
        }

        int initialUploadedCount = task.getTotalChunks() - missingChunks.size();
        UploadListener listener = listenerCache.get(taskKey);
        task.setUploadChunksCount(new AtomicInteger(initialUploadedCount));

        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            CompletableFuture<?>[] uploadFutures = missingChunks.stream()
                    .map(chunkIndex -> CompletableFuture.runAsync(() -> {
                        try {
                            byte[] chunkData = readChunk(channel, chunkIndex, task.getChunkSize(), task.getTotalSize());
                            applyRateLimit(chunkData.length, traceId);
                            if (listener != null) {
                                handleListenerBeforeSend(taskKey, task);
                            }
                            ApiResponse<ChunkUploadResponse> uploadResponse = uploadChunk(task.getRemoteAgentApiUrl(), task.getTransferId(), chunkIndex, chunkData, localPath, remotePath, traceId);
                            if (!uploadResponse.isSuccess()) {
                                throw new IOException("Chunk upload failed: " + uploadResponse.getMsg());
                            }
                            task.incrementUploadChunksCount();
                            int currentUploaded = task.getUploadChunksCount().get();
                            task.updateTimestamp();
                            this.inflightTasks.put(task.getTransferId(), task);
                            if (listener != null) {
                                double progress = (double) currentUploaded / task.getTotalChunks() * 100.0;
                                handleListenerProgress(taskKey, task.getTotalChunks(), currentUploaded, progress);
                            }
                        } catch (Exception e) {
                            throw new CompletionException("Failed to upload chunk " + chunkIndex, e);
                        }
                    }, chunkExecutor))
                    .toArray(CompletableFuture[]::new);

            CompletableFuture<Void> allUploads = CompletableFuture.allOf(uploadFutures);

            // Calculate a reasonable timeout
            int chunksToUpload = missingChunks.size();
            long waves = (long) Math.ceil((double) chunksToUpload / this.concurrentThreads);
            long timeoutSeconds = (waves + 2) * this.requestTimeoutSeconds; // Add a 2-request buffer
            timeoutSeconds = Math.max(60, timeoutSeconds); // Minimum 60 seconds

            try {
                allUploads.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new IOException("Chunk uploads timed out after " + timeoutSeconds + " seconds", e);
            }
        } catch (Exception e) {
            throw new IOException("Failed to upload chunks", e);
        }
    }

    private static final Type API_RESPONSE_CHUNK_STATUS = TypeToken.getParameterized(ApiResponse.class, ChunkStatusResponse.class).getType();
    private static final Type API_RESPONSE_CHUNK_INIT = TypeToken.getParameterized(ApiResponse.class, ChunkInitResponse.class).getType();
    private static final Type API_RESPONSE_CHUNK_UPLOAD = TypeToken.getParameterized(ApiResponse.class, ChunkUploadResponse.class).getType();
    private static final Type API_RESPONSE_MERGE_RESULT = TypeToken.getParameterized(ApiResponse.class, ChunkMergeResponse.class).getType();
    private static final Type API_RESPONSE_BOOLEAN = TypeToken.getParameterized(ApiResponse.class, Boolean.class).getType();

    private ApiResponse<ChunkStatusResponse> fetchLatestStatus(String remoteAgentApiUrl, String transferId, String traceId) throws IOException, InterruptedException {
        return getUploadStatus(remoteAgentApiUrl,transferId, traceId);
    }

    private byte[] readChunk(FileChannel channel, int chunkIndex, int chunkSize, long totalSize) throws IOException {
        long start = (long) chunkIndex * chunkSize;
        long length = Math.min(chunkSize, totalSize - start);

        // Using Netty's PooledByteBufAllocator for better performance
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer((int) length);
        try {
            int bytesRead = buf.writeBytes(channel, start, (int) length);
            if (bytesRead < length) {
                throw new IOException("Unexpected EOF reached at " + (start + bytesRead) + ". Expected " + length + " bytes, read " + bytesRead);
            }
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return data;
        } finally {
            buf.release();
        }
    }

    private ApiResponse<ChunkInitResponse> initUpload(UploadTask task) throws IOException, InterruptedException {
        logger.debug("[traceId={}] Initializing new upload: local={}, remote={}, size={}", task.getTraceId(), task.getLocalFilePath(), task.getRemoteTargetPath(), task.getTotalSize());
        ChunkInitRequest req = new ChunkInitRequest();
        req.setTotalSize(task.getTotalSize());
        req.setTransferId(task.getTransferId());

        if (agentConfig != null) {
            req.setSourceAgentId(agentConfig.getAgentId());
            req.setSourceAgentIp(agentConfig.getAgentIp());
            req.setSourceAgentPort(agentConfig.getServerPort());
        }
        File sourceFile = Paths.get(task.getLocalFilePath()).toFile();
        req.setSourceFileDir(Util.transferToLinuxPath(sourceFile.getParent()));
        req.setSourceFileName(sourceFile.getName());

        File destFile = Paths.get(task.getRemoteTargetPath()).toFile();
        req.setDestFileDir(Util.transferToLinuxPath(destFile.getParent()));
        req.setDestFileName(destFile.getName());
        try {
            URI uri = new URI(task.getRemoteAgentApiUrl());
            req.setDestAgentIp(uri.getHost());
            req.setDestAgentPort(uri.getPort());
        } catch (java.net.URISyntaxException e) {
            logger.warn("Could not parse agentApiUrl to extract host and port", e);
        }
        return postApi(task.getRemoteAgentApiUrl(),"api/file/chunk/init", req, API_RESPONSE_CHUNK_INIT, task.getTraceId());
    }

    private ApiResponse<ChunkStatusResponse> getUploadStatus(String remoteAgentApiUrl, String transferId, String traceId) throws IOException, InterruptedException {
        return getApi(remoteAgentApiUrl ,"api/file/chunk/status?transferId=" + transferId, API_RESPONSE_CHUNK_STATUS, traceId);
    }

    private ApiResponse<ChunkUploadResponse> uploadChunk(String remoteAgentApiUrl, String transferId, int chunkIndex, byte[] data, String localPath, String remotePath, String traceId) throws IOException, InterruptedException {
        logger.debug("[traceId={}] Uploading chunk {} for transferId: {}", traceId, chunkIndex, transferId);
        ChunkUploadRequest req = new ChunkUploadRequest();
        req.setTransferId(transferId);
        req.setChunkIndex(chunkIndex);
        req.setChunkSize(data.length);
        req.setContent(Base64.getEncoder().encodeToString(data));
        req.setEncoding("base64");

        if (agentConfig != null) {
            req.setSourceAgentId(agentConfig.getAgentId());
            req.setSourceAgentIp(agentConfig.getAgentIp());
            req.setSourceAgentPort(agentConfig.getServerPort());

            File sourceFile = Paths.get(localPath).toFile();
            req.setSourceFileDir(Util.transferToLinuxPath(sourceFile.getParent()));
            req.setSourceFileName(sourceFile.getName());

            File destFile = Paths.get(remotePath).toFile();
            req.setDestFileDir(Util.transferToLinuxPath(destFile.getParent()));
            req.setDestFileName(destFile.getName());
        }

        try {
            URI uri = new URI(remoteAgentApiUrl);
            req.setDestAgentIp(uri.getHost());
            req.setDestAgentPort(uri.getPort());
        } catch (java.net.URISyntaxException e) {
            logger.warn("[traceId={}] Could not parse agentApiUrl to extract host and port", traceId, e);
        }

        return postApi(remoteAgentApiUrl,"api/file/chunk/upload", req, API_RESPONSE_CHUNK_UPLOAD, traceId);
    }

    private void mergeChunks(String remoteAgentApiUrl, String transferId, String traceId) throws IOException, InterruptedException {
        logger.debug("[traceId={}] Merging chunks for transferId: {}", traceId, transferId);
        ChunkMergeRequest req = new ChunkMergeRequest();
        req.setTransferId(transferId);
        ApiResponse<ChunkMergeResponse> response = postApi(remoteAgentApiUrl,"api/file/chunk/merge", req, API_RESPONSE_MERGE_RESULT, traceId);

        ChunkMergeResponse data = response.getData();
        if (data == null) throw new IOException("Empty merge result");

        String fullPath = data.getDestFileDir();
        if (fullPath != null && !fullPath.endsWith("/") && !fullPath.endsWith("\\")) {
            fullPath += "/";
        }
        fullPath += data.getDestFileName();

        logger.debug("[traceId={}] Merge successful: path={}, size={}, checksum={}", traceId, fullPath, data.getSize(), data.getChecksum());
    }

    private boolean verifyRemoteFileExists(String remoteAgentApiUrl, String remotePath, String traceId) throws IOException, InterruptedException {
        logger.debug("[traceId={}] Verifying remote file exists: {}", traceId, remotePath);
        String encodedPath = java.net.URLEncoder.encode(remotePath, StandardCharsets.UTF_8);
        ApiResponse<Boolean> response = getApi(remoteAgentApiUrl,"api/file/exists?path=" + encodedPath, API_RESPONSE_BOOLEAN, traceId);
        if (!response.isSuccess()) {
            logger.debug("[traceId={}] Verification failed for '{}': {}", traceId, remotePath, response.getMsg());
            return false;
        }
        Boolean data = response.getData();
        logger.debug("[traceId={}] Verification result for '{}': {}", traceId, remotePath, data);
        return Boolean.TRUE.equals(data);
    }

    public List<UploadTask> getInflightTasks(int page, int pageSize) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (pageSize < 1 || pageSize > 1000) {
            throw new IllegalArgumentException("pageSize must be between 1 and 1000");
        }
        int offset = (page - 1) * pageSize;
        List<UploadTask> allTasks = new ArrayList<>(inflightTasks.values());
        int fromIndex = Math.min(offset, allTasks.size());
        int toIndex = Math.min(fromIndex + pageSize, allTasks.size());
        return allTasks.subList(fromIndex, toIndex);
    }

    public List<UploadTask> getAllInflightTasks() {
        return new ArrayList<>(inflightTasks.values());
    }

    public int getInflightTasksCount() {
        return inflightTasks.size();
    }

    public boolean isInflightTasksEmpty() {
        return inflightTasks.isEmpty();
    }

    void clearAllInflightTasks() {
        inflightTasks.clear();
    }

    /**
     * 填充监听器状态恢复字段到 UploadTask
     * 用于重启恢复场景，确保从JSON反序列化后能正确恢复监听器状态
     *
     * @param task 上传任务对象
     * @param listener 监听器实例（可能是BatchUploadListener）
     */
    private void populateRestoreFields(UploadTask task, UploadListener listener) {
        if (task == null || listener == null) {
            return;
        }

        // 只处理 BatchUploadListener 类型
        if (listener instanceof BatchUploadListener batchListener) {
            try {
                // 从 BatchUploadListener 提取恢复字段
                task.setTaskId(batchListener.getTaskId());
                task.setSubtaskId(batchListener.getSubtaskId());
                task.setFileName(batchListener.getFileName());
                
                // fileSize 已经在创建 UploadTask 时设置，这里从 listener 获取更准确的值
                if (batchListener.getFileSize() > 0) {
                    task.setFileSize(batchListener.getFileSize());
                }

                logger.debug("✅ 已填充恢复字段: taskId={}, subtaskId={}, fileName={}, size={}bytes",
                    task.getTaskId(), task.getSubtaskId(), task.getFileName(), task.getFileSize());

            } catch (Exception e) {
                logger.warn("⚠️ 填充恢复字段失败（不影响正常上传）: {}", e.getMessage());
            }
        }
    }

    /**
     * 重新提交失败的任务（内部方法，供 RetryManager 调用）
     * 与 uploadFile() 的区别：
     * - 不重新扫描文件
     * - 直接使用已有的任务对象
     * - 跳过文件存在性检查
     *
     * @param task 已有的 UploadTask 对象（包含 localFilePath, remoteTargetPath 等）
     */
    public void resubmitTask(UploadTask task) {
        String taskKey = getTaskKey(task);
        String traceId = task.getTraceId();

        logger.info("[traceId={}] 🔄 重新提交失败任务: transferId={}, file={}, retryCount={}, remoteTarget={}",
            traceId,
            task.getTransferId(),
            task.getLocalFilePath(),
            task.getRetryCount(),
            task.getRemoteTargetPath());

        // 直接将任务对象放入工作队列（worker线程会自动poll并处理）
        boolean offered = taskQueue.offer(task);

        if (offered) {
            logger.debug("✅ 任务已加入工作队列: taskKey={}", taskKey);
        } else {
            logger.error("❌ 任务加入工作队列失败: taskKey={}", taskKey);
        }
    }

    /**
     * 获取失败队列目录（用于日志和监控）
     */
    public Path getFailedQueueDir() {
        return failedQueueDir;
    }

    /**
     * 恢复监听器状态（用于重启恢复场景）
     * 当从JSON反序列化恢复任务时，Listener通过无参构造函数创建，
     * 需要调用此方法恢复完整状态，包括taskId、subtaskId、文件信息等。
     * 优先使用全局ProgressReporter实例（避免重复创建），如果未设置则创建新实例
     *
     * @param listener 通过反射创建的监听器实例
     * @param task 包含恢复信息的任务对象
     */
    private void restoreListenerState(UploadListener listener, UploadTask task) {
        if (listener == null || task == null) {
            logger.debug("⚠️ listener或task为null，跳过状态恢复");
            return;
        }

        // 只处理BatchUploadListener类型
        if (listener instanceof BatchUploadListener batchListener) {
            // 检查是否需要恢复（如果已经是有参构造函数创建的则跳过）
            if (!batchListener.isRestored()) {
                logger.debug("ℹ️ 监听器已通过有参构造函数初始化，跳过状态恢复: subtask={}",
                    batchListener.getSubtaskId());
                return;
            }

            Long taskId = task.getTaskId();
            Long subtaskId = task.getSubtaskId();
            String fileName = task.getFileName();
            String filePath = task.getLocalFilePath();
            long fileSize = task.getFileSize();

            if (subtaskId == null) {
                logger.warn("⚠️ subtaskId为null，无法恢复监听器状态: file={}", fileName);
                return;
            }

            try {
                // ★ 使用全局ProgressReporter，避免每次都新建
                ProgressReporter reporter = getOrCreateGlobalProgressReporter();

                // 恢复监听器状态
                batchListener.restoreState(taskId, subtaskId, filePath, fileName, fileSize, reporter);

                logger.info("✅ 上传监听器状态已恢复: subtaskId={}, file={}, size={}bytes",
                    subtaskId, fileName, fileSize);

            } catch (Exception e) {
                logger.error("❌ 恢复上传监听器状态失败: subtaskId={}, file={}, error={}",
                    subtaskId, fileName, e.getMessage());
            }
        }
    }

    /**
     * 获取或创建全局ProgressReporter实例
     * 优先使用已设置的全局实例，避免重复创建浪费资源
     */
    private ProgressReporter getOrCreateGlobalProgressReporter() {
        // 优先使用全局实例
        if (globalProgressReporter != null) {
            return globalProgressReporter;
        }

        // 全局实例未设置时，记录警告并创建临时实例（向后兼容）
        logger.warn("⚠️ globalProgressReporter未设置，将创建临时实例（建议在AgentApplication中调用setGlobalProgressReporter）");
        return createTemporaryProgressReporter();
    }

    /**
     * 创建临时的ProgressReporter实例（仅作为降级方案）
     */
    private ProgressReporter createTemporaryProgressReporter() {
        String proxyUrl = getRegistryServerUrl();
        if (proxyUrl == null || proxyUrl.isBlank()) {
            logger.debug("⚠️ proxyUrl未配置，使用空ProgressReporter");
            return new ProgressReporter(null);
        }

        return new ProgressReporter(proxyUrl);
    }

    /**
     * 获取注册中心/代理服务器URL（用于进度上报）
     */
    private String getRegistryServerUrl() {
        if (agentConfig == null) return null;
        
        // 尝试获取第一个注册服务器URL
        java.util.List<String> urls = agentConfig.getRegistryServerUrls();
        if (urls != null && !urls.isEmpty()) {
            return urls.getFirst();
        }
        
        return null;
    }
}