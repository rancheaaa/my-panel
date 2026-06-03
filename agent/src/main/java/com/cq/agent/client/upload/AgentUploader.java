package com.cq.agent.client.upload;

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
 * Implements UploadService interface for decorator pattern support.
 */
public class AgentUploader extends BaseAgentClient<UploadTask, UploadListener> implements UploadService {

    private static final Logger logger = LoggerFactory.getLogger(AgentUploader.class);

    private final AgentConfig agentConfig;

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
     * 
     * @return 配置对象
     */
    @Override
    public AgentConfig getAgentConfig() {
        return agentConfig;
    }

    @Override
    protected void processTask(UploadTask task) {
        String transferId = task.getTransferId();
        String traceId = task.getTraceId();

        try {
            task.setScannedStartTime(Util.currentTime());
            updateTaskStatus(task, UploadTaskStatus.SCANNED);

            String listenerClassName = task.getListenerClassName();
            if (listenerClassName != null && !listenerCache.containsKey(transferId)) {
                UploadListener listener = createListenerInstance(listenerClassName, UploadListener.class);
                if (listener != null) {
                    listenerCache.put(transferId, listener);
                }
            }

            // 尽早调用listener.onBeforeSend，确保taskId等元数据在任务处理前就被设置
            // 这样即使任务在initUpload阶段失败，taskId也不会为null，重试时不会被跳过
            UploadListener earlyListener = listenerCache.get(transferId);
            if (earlyListener != null) {
                earlyListener.onBeforeSend(task);
            }

            String fileCheck = Util.checkLocalFileReadable(task.getLocalFilePath());
            if (fileCheck != null) {
                throw new IOException(fileCheck);
            }
            task.setScannedEndTime(Util.currentTime());
            task.setStatus(UploadTaskStatus.INIT_UPLOADING);
            task.setInitUploadStartTime(Util.currentTime());
            updateTaskStatus(task, UploadTaskStatus.INIT_UPLOADING);

            int chunkSize;
            int totalChunks;
            List<Integer> missingChunks;
            logger.debug("[traceId={}] Get upload session status with transferId: {}", traceId, transferId);
            ApiResponse<ChunkStatusResponse> resp = getUploadStatus(task);
            if (resp.getData() == null) {
                ApiResponse<ChunkInitResponse> chunkInitResponse = initUpload(task);
                if (!chunkInitResponse.isSuccess() || chunkInitResponse.getData() == null) {
                    String errorMsg = chunkInitResponse.getMsg() != null ? chunkInitResponse.getMsg()
                            : "initUpload returned null data";
                    throw new IOException("Upload init failed: " + errorMsg);
                }
                logger.debug("[traceId={}] Upload initialized: transferId={}, totalChunks={}, chunkSize={}",
                        traceId, chunkInitResponse.getData().getTransferId(),
                        chunkInitResponse.getData().getTotalChunks(), chunkInitResponse.getData().getChunkSize());
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
                uploadChunks(task);

                ApiResponse<ChunkStatusResponse> finalStatus = fetchLatestStatus(task);
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
                ApiResponse<ChunkStatusResponse> latestStatus = fetchLatestStatus(task);
                logger.error("[traceId={}] Chunk upload failed, status: {} {}", traceId, latestStatus, e.getMessage());
                throw e;
            }

            try {
                task.setMergeChunksStartTime(Util.currentTime());
                updateTaskStatus(task, UploadTaskStatus.MERGING_CHUNKS);
                mergeChunks(task);
                task.setMergeChunksEndTime(Util.currentTime());
                updateTaskStatus(task, UploadTaskStatus.MERGE_CHUNKS_COMPLETED);
            } catch (IOException e) {
                ApiResponse<ChunkStatusResponse> latestStatus = fetchLatestStatus(task);
                logger.error("[traceId={}] Merge failed, status:{}  {}", traceId, latestStatus, e.getMessage());
                throw e;
            }

            task.setVerifyStartTime(Util.currentTime());
            updateTaskStatus(task, UploadTaskStatus.VERIFYING_CHUNKS);
            if (verifyRemoteFileExists(task)) {
                task.setVerifyEndTime(Util.currentTime());
                updateTaskStatus(task, UploadTaskStatus.VERIFY_CHUNKS_COMPLETED);
                task.setUploadSuccessTime(Util.currentTime());
                updateTaskStatus(task, UploadTaskStatus.UPLOAD_SUCCESS);
                handleListenerSuccess(transferId, task);
                // 传输真正成功，移除listener
                removeListener(transferId);
                inflightTasks.remove(task.getTransferId());
            } else {
                logger.error("[traceId={}] Verify remote file {} failed", traceId, task.getRemoteTargetPath());
                throw new IOException("Upload failed: remote file not found");
            }
            logger.info("[traceId={}] Upload task completed: {}", traceId, transferId);
        } catch (Exception e) {
            logger.error("[traceId={}] Upload task failed: {} - {}", traceId, transferId, e.getMessage(), e);
            task.setExceptionDesc(e.getMessage());
            updateTaskStatus(task, UploadTaskStatus.FAILED);

            if (metaStore != null && failedQueueDir != null) {
                try {
                    metaStore.moveToFailedQueue(task.getTransferId(), failedQueueDir);
                } catch (IOException ex) {
                    logger.warn("[traceId={}] 移动失败任务到重试队列失败: {}", traceId, ex.getMessage());
                }
            }

            handleListenerError(transferId, e.getMessage());
            inflightTasks.remove(task.getTransferId());
            // 失败时不移除listener：任务可能被移到失败队列等待重试，
            // listener需要保留以便重试时复用，只有在真正传输成功时才移除
        }
    }

    public void updateTaskStatus(UploadTask task, UploadTaskStatus status) {
        try {
            task.setStatus(status);
            task.updateTimestamp();
        } catch (Exception e) {
            logger.error("更新任务状态失败: {}", e.getMessage());
        }

        inflightTasks.put(task.getTransferId(), task);

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
                String errorMsg = "Upload queue depth is reached: " + maxQueueDepth;
                logger.error(errorMsg);
                return false;
            }
            if (localFilePath == null || localFilePath.isBlank()) {
                String errorMsg = "localFilePath must not be null or blank";
                logger.error(errorMsg);
                return false;
            }
            // remoteTargetPath like 192.168.1.100:7777@root:/tmp/upload
            // ip:port@username:destFilePath
            if (remoteTargetInfo == null || remoteTargetInfo.isBlank()) {
                String errorMsg = "remoteTargetPath must not be null or blank";
                logger.error(errorMsg);
                return false;
            }

            RemoteAgentInfo remoteAgentInfo;
            try {
                remoteAgentInfo = Util.resolveRemoteAgentInfo(remoteTargetInfo);
            } catch (Exception e) {
                return false;
            }

            if (!new File(localFilePath).isAbsolute()) {
                String errorMsg = "localFilePath must be an absolute path: " + localFilePath;
                logger.error(errorMsg);
                return false;
            }

            String fileCheck = Util.checkLocalFileReadable(localFilePath);
            if (fileCheck != null) {
                logger.error("[traceId={}] Local file validation failed: {}", traceId, fileCheck);
                return false;
            }

            File localFile = new File(localFilePath);
            long fileSize = localFile.length();
            long maxFileSize = agentConfig.getMaxFileSize();
            if (maxFileSize > 0 && fileSize > maxFileSize) {
                String errorMsg = "File size exceeds maximum allowed size: " + maxFileSize + "bytes";
                logger.error("[traceId={}] {}", traceId, errorMsg);
                return false;
            }

            logger.debug("[traceId={}] Local file validated: path={}, size={} bytes", traceId, localFilePath, fileSize);

            UploadTask task = new UploadTask(localFilePath, remoteAgentInfo.getDestFilePath(), fileSize,
                    "http://" + remoteAgentInfo.getIp() + ":" + remoteAgentInfo.getPort() + "/",
                    remoteAgentInfo.getUsername());
            task.setTransferId(UUID.randomUUID().toString().replace("-", ""));
            task.setTraceId(traceId);
            task.setEnqueuedTime(Util.currentTime());
            task.setListenerClassName(listener != null ? listener.getClass().getName() : null);

            // 设置原始文件名：如果路径是隐藏文件，推算原始文件名
            Path localPathObj = Path.of(localFilePath);
            if (TransferFileStateManager.isTransferringFile(localPathObj)) {
                task.setFileName(TransferFileStateManager.getOriginalPath(localPathObj).getFileName().toString());
            } else {
                task.setFileName(localPathObj.getFileName().toString());
            }
            task.setPriority(agentConfig.getUploadTaskGlobalPriority());
            task.updateTimestamp();

            if (listener != null) {
                listenerCache.put(task.getTransferId(), listener);
            }

            if (metaStore != null) {
                metaStore.saveTask(task);
            }

            if (!this.inflightTasks.containsKey(task.getTransferId())) {
                this.inflightTasks.put(task.getTransferId(), task);
            }
            boolean offered = taskQueue.offer(task);
            if (!offered) {
                // offer失败时从inflightTasks移除，避免泄漏导致hasInflightUploadForFile误判
                inflightTasks.remove(task.getTransferId());
            }
            return offered;
        } catch (Exception e) {
            logger.error("upload file with error!", e);
            handleListenerError(listener, "upload file with error!");
            return false;
        }
    }

    @Override
    public boolean resubmitTask(UploadTask task, UploadListener listener) {
        String transferId = task.getTransferId();
        try {
            if (listener != null) {
                listenerCache.put(transferId, listener);
            }
            inflightTasks.put(transferId, task);
            boolean offered = taskQueue.offer(task);
            if (!offered) {
                // offer失败时从inflightTasks移除，避免泄漏导致hasInflightUploadForFile误判
                inflightTasks.remove(transferId);
            }
            logger.info("[transferId={}] 任务重新提交到处理队列: offered={}", transferId, offered);
            return offered;
        } catch (Exception e) {
            logger.error("[transferId={}] 重新提交任务失败: {}", transferId, e.getMessage(), e);
            inflightTasks.remove(task.getTransferId());
            handleListenerError(listener, "resubmit task failed: " + e.getMessage());
            return false;
        }
    }

    private void uploadChunks(UploadTask task) throws IOException {
        File file = new File(task.getLocalFilePath());
        String transferId = task.getTransferId();
        String traceId = task.getTraceId();
        List<Integer> missingChunks = task.getMissingChunks();
        if (missingChunks.isEmpty()) {
            logger.debug("No missing chunks to upload for task: {}", transferId);
            return;
        }

        int totalToUpload = missingChunks.size();
        int initialUploadedCount = task.getTotalChunks() - missingChunks.size();
        UploadListener listener = listenerCache.get(transferId);
        task.setUploadChunksCount(new AtomicInteger(initialUploadedCount));
        AtomicInteger failedCount = new AtomicInteger(0);
        CountDownLatch completionLatch = new CountDownLatch(totalToUpload);

        List<CompletableFuture<Void>> uploadFutures = new ArrayList<>();

        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            for (int i = 0; i < totalToUpload; i++) {
                final int chunkIndex = missingChunks.get(i);
                byte[] chunkData = readChunk(channel, chunkIndex, task.getChunkSize(), task.getTotalSize());
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        if (listener != null) {
                            handleListenerBeforeSend(transferId, task);
                        }
                        ApiResponse<ChunkUploadResponse> uploadResponse = uploadChunk(task, chunkIndex, chunkData);
                        if (!uploadResponse.isSuccess()) {
                            throw new IOException("Chunk upload failed: " + uploadResponse.getMsg());
                        }
                        task.incrementUploadChunksCount();
                        int currentUploaded = task.getUploadChunksCount().get();
                        task.updateTimestamp();
                        this.inflightTasks.put(task.getTransferId(), task);
                        if (listener != null) {
                            double progress = (double) currentUploaded / task.getTotalChunks() * 100.0;
                            handleListenerProgress(transferId, task.getTotalChunks(), currentUploaded, progress);
                        }
                    } catch (Exception e) {
                        failedCount.incrementAndGet();
                        logger.error("[traceId={}] Chunk {} upload failed: {}", traceId, chunkIndex, e.getMessage());
                        throw new CompletionException("Failed to upload chunk " + chunkIndex, e);
                    } finally {
                        completionLatch.countDown();
                    }
                }, chunkExecutor);
                uploadFutures.add(future);
                applyRateLimit(chunkData.length, traceId);
            }

            long waves = (long) Math.ceil((double) totalToUpload / this.concurrentThreads);
            long estimatedRateLimitMsPerChunk = estimateRateLimitTimeMsPerChunk(task.getChunkSize());
            long perChunkTimeMs = Math.max(this.requestTimeoutSeconds * 1000L, estimatedRateLimitMsPerChunk);
            long timeoutMs = Math.max(60_000L, (waves + 2) * perChunkTimeMs);

            boolean completedInTime;
            try {
                completedInTime = completionLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancelAllFutures(uploadFutures);
                throw new IOException("Chunk upload interrupted", e);
            }

            if (!completedInTime) {
                cancelAllFutures(uploadFutures);
                int succeeded = totalToUpload - failedCount.get();
                throw new IOException(String.format(
                        "Chunk uploads timed out after %d seconds: %d/%d chunks completed (%d failed), %d still pending",
                        timeoutMs / 1000, succeeded, totalToUpload, failedCount.get(), completionLatch.getCount()));
            }

            int failed = failedCount.get();
            if (failed > 0) {
                int succeeded = totalToUpload - failed;
                throw new IOException(String.format(
                        "%d/%d chunks failed to upload (%d succeeded)", failed, totalToUpload, succeeded));
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to upload chunks", e);
        }
    }

    private long estimateRateLimitTimeMsPerChunk(int chunkSize) {
        if (rateLimiter == null || maxRateKBPerSecond <= 0 || chunkSize <= 0) {
            return 0;
        }
        return (long) ((double) chunkSize / (maxRateKBPerSecond * 1024L)) * 1000;
    }

    private void cancelAllFutures(List<CompletableFuture<Void>> futures) {
        for (CompletableFuture<Void> future : futures) {
            future.cancel(true);
        }
    }

    private static final Type API_RESPONSE_CHUNK_STATUS = TypeToken
            .getParameterized(ApiResponse.class, ChunkStatusResponse.class).getType();
    private static final Type API_RESPONSE_CHUNK_INIT = TypeToken
            .getParameterized(ApiResponse.class, ChunkInitResponse.class).getType();
    private static final Type API_RESPONSE_CHUNK_UPLOAD = TypeToken
            .getParameterized(ApiResponse.class, ChunkUploadResponse.class).getType();
    private static final Type API_RESPONSE_MERGE_RESULT = TypeToken
            .getParameterized(ApiResponse.class, ChunkMergeResponse.class).getType();
    private static final Type API_RESPONSE_BOOLEAN = TypeToken.getParameterized(ApiResponse.class, Boolean.class)
            .getType();

    private ApiResponse<ChunkStatusResponse> fetchLatestStatus(UploadTask task)
            throws IOException, InterruptedException {
        return getUploadStatus(task);
    }

    private ApiResponse<ChunkStatusResponse> getUploadStatus(UploadTask task) throws IOException, InterruptedException {
        return getApi(task.getRemoteAgentApiUrl(), "api/file/chunk/status?transferId=" + task.getTransferId(),
                API_RESPONSE_CHUNK_STATUS, task.getTraceId());
    }

    protected byte[] readChunk(FileChannel channel, int chunkIndex, int chunkSize, long totalSize) throws IOException {
        long start = (long) chunkIndex * chunkSize;
        long length = Math.min(chunkSize, totalSize - start);

        // Using Netty's PooledByteBufAllocator for better performance
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer((int) length);
        try {
            int bytesRead = buf.writeBytes(channel, start, (int) length);
            if (bytesRead < length) {
                throw new IOException("Unexpected EOF reached at " + (start + bytesRead) + ". Expected " + length
                        + " bytes, read " + bytesRead);
            }
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return data;
        } finally {
            buf.release();
        }
    }

    private ApiResponse<ChunkInitResponse> initUpload(UploadTask task) throws IOException, InterruptedException {
        logger.debug("[traceId={}] Initializing new upload: local={}, remote={}, size={}", task.getTraceId(),
                task.getLocalFilePath(), task.getRemoteTargetPath(), task.getTotalSize());
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
        // 优先使用task中保存的原始文件名，避免隐藏文件名（.{name}.transferring）被发送到远程
        req.setSourceFileName(task.getFileName() != null ? task.getFileName() : sourceFile.getName());

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
        return postApi(task.getRemoteAgentApiUrl(), "api/file/chunk/init", req, API_RESPONSE_CHUNK_INIT,
                task.getTraceId());
    }

    protected ApiResponse<ChunkUploadResponse> uploadChunk(UploadTask task, int chunkIndex, byte[] data)
            throws IOException, InterruptedException {
        logger.debug("[traceId={}] Uploading chunk {} for transferId: {}", task.getTraceId(), chunkIndex,
                task.getTransferId());
        ChunkUploadRequest req = new ChunkUploadRequest();
        req.setTransferId(task.getTransferId());
        req.setChunkIndex(chunkIndex);
        req.setChunkSize(data.length);
        req.setContent(Base64.getEncoder().encodeToString(data));
        req.setEncoding("base64");

        if (agentConfig != null) {
            req.setSourceAgentId(agentConfig.getAgentId());
            req.setSourceAgentIp(agentConfig.getAgentIp());
            req.setSourceAgentPort(agentConfig.getServerPort());

            File sourceFile = Paths.get(task.getLocalFilePath()).toFile();
            req.setSourceFileDir(Util.transferToLinuxPath(sourceFile.getParent()));
            // 优先使用task中保存的原始文件名，避免隐藏文件名被发送到远程
            req.setSourceFileName(task.getFileName() != null ? task.getFileName() : sourceFile.getName());

            File destFile = Paths.get(task.getRemoteTargetPath()).toFile();
            req.setDestFileDir(Util.transferToLinuxPath(destFile.getParent()));
            req.setDestFileName(destFile.getName());
        }

        try {
            URI uri = new URI(task.getRemoteAgentApiUrl());
            req.setDestAgentIp(uri.getHost());
            req.setDestAgentPort(uri.getPort());
        } catch (java.net.URISyntaxException e) {
            logger.warn("[traceId={}] Could not parse agentApiUrl to extract host and port", task.getTraceId(), e);
        }

        return postApi(task.getRemoteAgentApiUrl(), "api/file/chunk/upload", req, API_RESPONSE_CHUNK_UPLOAD,
                task.getTraceId());
    }

    private void mergeChunks(UploadTask task) throws IOException, InterruptedException {
        logger.debug("[traceId={}] Merging chunks for transferId: {}", task.getTraceId(), task.getTransferId());
        ChunkMergeRequest req = new ChunkMergeRequest();
        req.setTransferId(task.getTransferId());
        ApiResponse<ChunkMergeResponse> response = postApi(task.getRemoteAgentApiUrl(), "api/file/chunk/merge", req,
                API_RESPONSE_MERGE_RESULT, task.getTraceId());

        ChunkMergeResponse data = response.getData();
        if (data == null)
            throw new IOException("Empty merge result");

        String fullPath = data.getDestFileDir();
        if (fullPath != null && !fullPath.endsWith("/") && !fullPath.endsWith("\\")) {
            fullPath += "/";
        }
        fullPath += data.getDestFileName();

        logger.debug("[traceId={}] Merge successful: path={}, size={}, checksum={}", task.getTraceId(), fullPath,
                data.getSize(), data.getChecksum());
    }

    private boolean verifyRemoteFileExists(UploadTask task) throws IOException, InterruptedException {
        logger.debug("[traceId={}] Verifying remote file exists: {}", task.getTraceId(), task.getRemoteTargetPath());
        String encodedPath = java.net.URLEncoder.encode(task.getRemoteTargetPath(), StandardCharsets.UTF_8);
        ApiResponse<Boolean> response = getApi(task.getRemoteAgentApiUrl(), "api/file/exists?path=" + encodedPath,
                API_RESPONSE_BOOLEAN, task.getTraceId());
        if (!response.isSuccess()) {
            logger.debug("[traceId={}] Verification failed for '{}': {}", task.getTraceId(), task.getRemoteTargetPath(),
                    response.getMsg());
            return false;
        }
        Boolean data = response.getData();
        logger.debug("[traceId={}] Verification result for '{}': {}", task.getTraceId(), task.getRemoteTargetPath(),
                data);
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

    /**
     * 检查指定文件路径是否有正在进行的上传任务
     * 用于广播模式下判断是否可以安全恢复隐藏文件
     */
    public boolean hasInflightUploadForFile(String localFilePath) {
        if (localFilePath == null || inflightTasks.isEmpty()) {
            return false;
        }
        return inflightTasks.values().stream()
                .anyMatch(task -> localFilePath.equals(task.getLocalFilePath()));
    }

    void clearAllInflightTasks() {
        inflightTasks.clear();
    }

    /**
     * 获取指定transferId的listener（供子类/装饰者访问）
     */
    public UploadListener getListener(String transferId) {
        return listenerCache.get(transferId);
    }

    /**
     * 移除指定transferId的listener（供子类/装饰者在确认最终状态后调用）
     */
    public void removeListener(String transferId) {
        listenerCache.remove(transferId);
    }

    /**
     * 将listener放入缓存（原子操作，如果已存在则保留旧的并返回旧值）
     * 供子类/装饰者调用
     */
    public UploadListener putListenerIfAbsent(String transferId, UploadListener listener) {
        return listenerCache.putIfAbsent(transferId, listener);
    }
}