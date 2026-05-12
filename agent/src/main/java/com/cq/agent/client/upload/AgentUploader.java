package com.cq.agent.client.upload;

import com.cq.agent.client.BaseAgentClient;
import com.cq.agent.client.RemoteAgentInfo;
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
 */
public class AgentUploader extends BaseAgentClient<UploadTask, UploadListener> {

    private static final Logger logger = LoggerFactory.getLogger(AgentUploader.class);

    private final AgentConfig agentConfig;

    public AgentUploader(AgentConfig agentConfig) {
        super(agentConfig, agentConfig.getUploadConcurrentUploads(),
                agentConfig.getUploadMaxQueueDepth(), agentConfig.getUploadWorkerCount(),
                agentConfig.getUploadMaxRetries(), agentConfig.getUploadRetryDelayMs(),
                agentConfig.getUploadConnectTimeoutSeconds(), agentConfig.getUploadRequestTimeoutSeconds(),
                agentConfig.getTransfersMetaDir(),
                agentConfig.getMaxUploadRateKBPerSecond(), UploadTask.class, "upload");
        this.agentConfig = agentConfig;
    }

    @Override
    protected void processTask(UploadTask task) {
        String taskKey = getTaskKey(task);
        String traceId = task.getTraceId();

        try {
            updateTaskStatus(task, UploadTaskStatus.SCANNED);
            task.incrementRetryCount();

            String listenerClassName = task.getListenerClassName();
            if (listenerClassName != null && !listenerCache.containsKey(taskKey)) {
                UploadListener listener = createListenerInstance(listenerClassName, UploadListener.class);
                if (listener != null) {
                    listenerCache.put(taskKey, listener);
                }
            }

            String fileCheck = Util.checkLocalFileReadable(task.getLocalFilePath());
            if (fileCheck != null) {
                throw new IOException(fileCheck);
            }

            File file = new File(task.getLocalFilePath());

            task.setStatus(UploadTaskStatus.INIT_UPLOADING);
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
            updateTaskStatus(task, UploadTaskStatus.INIT_UPLOAD_COMPLETED);

            try {
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
                updateTaskStatus(task, UploadTaskStatus.UPLOAD_CHUNKS_COMPLETED);
            } catch (IOException e) {
                ApiResponse<ChunkStatusResponse> latestStatus = fetchLatestStatus(task.getRemoteAgentApiUrl(), task.getTransferId(), traceId);
                logger.error("[traceId={}] Chunk upload failed, status: {} {}", traceId, latestStatus, e.getMessage());
                throw e;
            }

            try {
                updateTaskStatus(task, UploadTaskStatus.MERGING_CHUNKS);
                mergeChunks(task.getRemoteAgentApiUrl(), task.getTransferId(), traceId);
                updateTaskStatus(task, UploadTaskStatus.MERGE_CHUNKS_COMPLETED);
            } catch (IOException e) {
                ApiResponse<ChunkStatusResponse> latestStatus = fetchLatestStatus(task.getRemoteAgentApiUrl(), task.getTransferId(), traceId);
                logger.error("[traceId={}] Merge failed, status:{}  {}", traceId, latestStatus, e.getMessage());
                throw e;
            }

            if (verifyRemoteFileExists(task.getRemoteAgentApiUrl(), task.getRemoteTargetPath(), traceId)) {
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
            updateTaskStatus(task, UploadTaskStatus.FAILED);
            task.setExceptionDesc(e.getMessage());
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

        AtomicInteger uploadedCount = new AtomicInteger(task.getTotalChunks() - missingChunks.size());
        UploadListener listener = listenerCache.get(taskKey);
        task.setUploadChunksCount(uploadedCount);

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
                            int currentUploaded = uploadedCount.incrementAndGet();
                            task.incrementUploadChunksCount();
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
}