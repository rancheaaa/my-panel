package com.cq.agent.client.upload;

import com.cq.agent.config.AgentConfig;
import com.cq.agent.dto.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent upload client for chunked file transfer.
 * Uses a persistent queue backed by RocksDB for durability.
 */
public class AgentUploader {

    private static final Logger logger = LoggerFactory.getLogger(AgentUploader.class);
    private static final int DEFAULT_MAX_QUEUE_DEPTH = 500;
    private static final int DEFAULT_WORKER_COUNT = 4;

    private final String agentApiUrl;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final PersistentQueue<UploadTask> taskQueue;
    private final PersistentMap<String, UploadTask> taskInflightMap;
    private final ConcurrentHashMap<String, UploadListener> listeners = new ConcurrentHashMap<>();
    private final ExecutorService uploadExecutor;
    private final ExecutorService workerExecutor;
    private final int workerCount;
    private final int concurrentUploads;
    private volatile boolean shutdown;

    private final AgentConfig agentConfig;

    private final int maxRetries;
    private final long retryDelayMs;
    private final int connectTimeoutSeconds;
    private final int requestTimeoutSeconds;

    private final int maxUploadRateKBPerSecond;
    private final UploadRateLimiter rateLimiter;

    public AgentUploader(AgentConfig agentConfig, String agentApiUrl, int concurrentUploads) {
        this(agentConfig, agentApiUrl, concurrentUploads, DEFAULT_MAX_QUEUE_DEPTH, DEFAULT_WORKER_COUNT, 3, 2000, 10, 60);
    }

    public AgentUploader(AgentConfig agentConfig, String agentApiUrl, int concurrentUploads, int maxQueueDepth, int workerCount,
                         int maxRetries, long retryDelayMs, int connectTimeoutSeconds, int requestTimeoutSeconds) {
        if (agentApiUrl == null || agentApiUrl.isBlank()) {
            throw new IllegalArgumentException("agentApiUrl must not be null or blank");
        }
        if (concurrentUploads < 1 || concurrentUploads > 64) {
            throw new IllegalArgumentException("concurrentUploads must be between 1 and 64");
        }
        if (maxQueueDepth < 1 || maxQueueDepth > 100_000) {
            throw new IllegalArgumentException("maxQueueDepth must be between 1 and 100000");
        }
        if (workerCount < 1 || workerCount > 32) {
            throw new IllegalArgumentException("workerCount must be between 1 and 32");
        }

        this.agentConfig = agentConfig;

        this.agentApiUrl = agentApiUrl.endsWith("/") ? agentApiUrl : agentApiUrl + "/";
        try {
            this.taskQueue = new PersistentQueue<>(agentConfig.getUploadQueueDbPath(), "upload-tasks", UploadTask.class);
            this.taskInflightMap = new PersistentMap<>(agentConfig.getUploadQueueDbPath(), "upload-inflight", String.class, UploadTask.class);
            logger.info("Persistent queue initialized at: {}", agentConfig.getUploadQueueDbPath());
            logger.info("Queue size on startup: {}", taskQueue.size());
            if (!taskQueue.isEmpty()) {
                logger.info("Resuming {} pending upload tasks from previous session", taskQueue.size());
            }
            this.maxRetries = Math.max(1, maxRetries);
            this.retryDelayMs = Math.max(500, retryDelayMs);
            this.connectTimeoutSeconds = Math.max(5, connectTimeoutSeconds);
            this.requestTimeoutSeconds = Math.max(30, requestTimeoutSeconds);

            this.maxUploadRateKBPerSecond = agentConfig.getMaxUploadRateKBPerSecond();
            if (this.maxUploadRateKBPerSecond > 0) {
                long bytesPerSecond = (long) this.maxUploadRateKBPerSecond * 1024;
                this.rateLimiter = new UploadRateLimiter(bytesPerSecond);
                logger.info("Rate limiting enabled: max upload rate = {} KB/s", this.maxUploadRateKBPerSecond);
            } else {
                this.rateLimiter = null;
                logger.info("Rate limiting disabled (maxUploadRateKBPerSecond = 0)");
            }

            this.httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(this.connectTimeoutSeconds))
                    .build();

            this.uploadExecutor = new ThreadPoolExecutor(
                    concurrentUploads,
                    concurrentUploads,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(),
                    r -> {
                        Thread t = new Thread(r, "agent-uploader-chunk");
                        t.setDaemon(false);
                        return t;
                    }
            );
            this.workerExecutor = new ThreadPoolExecutor(
                    workerCount,
                    workerCount,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(),
                    r -> {
                        Thread t = new Thread(r, "agent-uploader-worker");
                        t.setDaemon(false);
                        return t;
                    }
            );
            this.workerCount = workerCount;
            this.concurrentUploads = concurrentUploads;
        } catch (Exception e) {
            logger.error("Failed to initialize AgentUploader: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize AgentUploader", e);
        }
    }

    public void init() {
        for (int i = 0; i < workerCount; i++) {
            final int id = i;
            workerExecutor.submit(() -> runWorker(id));
        }
        logger.info("AgentUploader initialized, queue size={}, workers={}", taskQueue.size(), workerCount);
    }

    private void runWorker(int id) {
        while (!shutdown) {
            UploadTask task = null;
            try {
                task = taskQueue.poll();
                if (task == null) {
                    Thread.sleep(1000);
                    continue;
                }
//                String taskKey = Util.md5(task.getLocalFilePath() + ":" + task.getRemoteTargetPath());
                processTask(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                if (task != null) {
                    String taskKey = Util.md5(task.getLocalFilePath() + ":" + task.getRemoteTargetPath());
                    handleListenerError(taskKey, UploadErrorClassifier.toUserMessage(t instanceof Exception ? (Exception) t : new Exception(t)));
                    listeners.remove(taskKey);
                }
                logger.error("Worker {} error", id, t);
            }
        }
    }

    public void uploadFile(String localFilePath, String remoteTargetPath, UploadListener listener) {
        // Generate traceid for this upload
        String traceId = java.util.UUID.randomUUID().toString().replace("-", "");

        try {
            if (localFilePath == null || localFilePath.isBlank()) {
                handleListenerError(listener, "localFilePath must not be null or blank");
                return;
            }
            if (remoteTargetPath == null || remoteTargetPath.isBlank()) {
                handleListenerError(listener, "remoteTargetPath must not be null or blank");
                return;
            }

            // Validate absolute paths
            if (!new File(localFilePath).isAbsolute()) {
                handleListenerError(listener, "localFilePath must be an absolute path: " + localFilePath);
                return;
            }

            // Validate local file exists and is readable
            String fileCheck = Util.checkLocalFileReadable(localFilePath);
            if (fileCheck != null) {
                logger.error("[traceId={}] Local file validation failed: {}", traceId, fileCheck);
                handleListenerError(listener, fileCheck);
                return;
            }

            File localFile = new File(localFilePath);
            long fileSize = localFile.length();
            // Validate file size against maximum allowed size
            long maxFileSize = agentConfig.getMaxFileSize();
            if (maxFileSize > 0 && fileSize > maxFileSize) {
                String errorMsg = "File size exceeds maximum allowed size: " + maxFileSize + "bytes";
                logger.error("[traceId={}] {}", traceId, errorMsg);
                handleListenerError(listener, errorMsg);
                return;
            }

            logger.debug("[traceId={}] Local file validated: path={}, size={} bytes", traceId, localFilePath, fileSize);

            String taskKey = Util.md5(localFilePath + ":" + remoteTargetPath);

            if (listener != null) {
                listeners.put(taskKey, listener);
            }

            UploadTask task = new UploadTask(localFilePath, remoteTargetPath);
            task.setTransferId(UUID.randomUUID().toString().replace("-", ""));
            task.setTraceId(traceId);
            task.setEnqueuedTime(Util.currentTime());
            taskQueue.offer(task);
        } catch (Exception e) {
            logger.error("upload file with error!", e);
            handleListenerError(listener, "upload file with error!");
        }
    }

    private void processTask(UploadTask task) {
        String taskKey = Util.md5(task.getLocalFilePath() + ":" + task.getRemoteTargetPath());
        String traceId = task.getTraceId();

        try {
            task.setStatus(UploadTaskStatus.SCANNED);
            task.incrementRetryCount();
            if (!this.taskInflightMap.containsKey(task.getTransferId())) {
                this.taskInflightMap.put(task.getTransferId(), task);
            }
            String fileCheck = Util.checkLocalFileReadable(task.getLocalFilePath());
            if (fileCheck != null) {
                throw new IOException(fileCheck);
            }

            File file = new File(task.getLocalFilePath());

            UploadResult finalResult;
            UploadState state;
            task.setStatus(UploadTaskStatus.INIT_UPLOADING);
            task.setInitUploadStartTime(Util.currentTime());
            this.taskInflightMap.put(task.getTransferId(), task);

            state = initOrResumeUpload(file, task.getLocalFilePath(), task.getRemoteTargetPath(), task.getTransferId(), traceId);
            task.setInitUploadEndTime(Util.currentTime());
            task.setStatus(UploadTaskStatus.INIT_UPLOAD_COMPLETED);

            if (task.getTransferId() == null || !task.getTransferId().equals(state.getTransferId())) {
                task.setTransferId(state.getTransferId());
            }
            task.setChunkSize(state.getChunkSize());
            task.setTotalChunks(state.getTotalChunks());
            this.taskInflightMap.put(task.getTransferId(), task);

            try {
                task.setStatus(UploadTaskStatus.UPLOADING_CHUNKS);
                task.setUploadChunksStartTime(Util.currentTime());
                this.taskInflightMap.put(task.getTransferId(), task);
                uploadChunks(task, file, state, taskKey, task.getLocalFilePath(), task.getRemoteTargetPath(), traceId);
                task.setUploadChunksEndTime(Util.currentTime());
                task.setStatus(UploadTaskStatus.UPLOAD_CHUNKS_COMPLETED);
                this.taskInflightMap.put(task.getTransferId(), task);
            } catch (IOException e) {
                state = fetchLatestState(state.getTransferId(), traceId);
                logger.error("[traceId={}] Chunk upload failed, status: {} {}", traceId, state, e.getMessage());
                throw e;
            }

            UploadResult mergeAttemptResult;
            try {
                task.setStatus(UploadTaskStatus.MERGING_CHUNKS);
                task.setMergeChunksStartTime(Util.currentTime());
                mergeAttemptResult = mergeChunks(state.getTransferId(), traceId);
                task.setMergeChunksEndTime(Util.currentTime());
                task.setStatus(UploadTaskStatus.MERGE_CHUNKS_COMPLETED);
            } catch (IOException e) {
                state = fetchLatestState(state.getTransferId(), traceId);
                logger.error("[traceId={}] Merge failed, status:{}  {}", traceId, state, e.getMessage());
                throw e;
            }
            if (verifyRemoteFileExists(task.getRemoteTargetPath(), traceId)) {
                finalResult = new UploadResult(task.getRemoteTargetPath(), mergeAttemptResult.getSize(),
                        mergeAttemptResult.getChecksum(), UploadCompletionState.VERIFIED);
                task.setCompletedTime(Util.currentTime());
                task.setStatus(UploadTaskStatus.UPLOAD_SUCCESS);
                handleListenerSuccess(taskKey, finalResult);
            } else {
                logger.error("[traceId={}] Verify remote file {} failed", traceId, task.getRemoteTargetPath());
                throw new IOException("Upload failed after all retries, but no result was produced.");
            }
            logger.info("[traceId={}] Upload task completed: {}", traceId, taskKey);
            listeners.remove(taskKey);
        } catch (Exception e) {
            String userMsg = UploadErrorClassifier.toUserMessage(e);
            logger.error("[traceId={}] Upload task failed: {} - {}", traceId, taskKey, userMsg, e);
            task.setStatus(UploadTaskStatus.FAILED);
            handleListenerError(taskKey, userMsg);
            listeners.remove(taskKey);
        }
    }

    private void uploadChunks(UploadTask task, File file, UploadState state, String taskKey, String localPath, String remotePath, String traceId) throws IOException {
        Set<Integer> missingChunks = state.getMissingChunks();
        if (missingChunks.isEmpty()) {
            logger.debug("No missing chunks to upload for task: {}", taskKey);
            return;
        }

        AtomicInteger uploadedCount = new AtomicInteger(state.getTotalChunks() - missingChunks.size());
        UploadListener listener = listeners.get(taskKey);

        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(file.toPath(), java.nio.file.StandardOpenOption.READ)) {
            CompletableFuture<?>[] uploadFutures = missingChunks.stream()
                    .map(chunkIndex -> CompletableFuture.runAsync(() -> {
                        try {
                            byte[] chunkData = readChunk(channel, chunkIndex, state.getChunkSize(), state.getTotalSize());
                            applyRateLimit(chunkData.length, traceId);
                            uploadChunk(state.getTransferId(), chunkIndex, chunkData, localPath, remotePath, traceId);
                            int currentUploaded = uploadedCount.incrementAndGet();
                            task.incrementUploadChunksCount();
                            this.taskInflightMap.put(task.getTransferId(), task);
                            if (listener != null) {
                                double progress = (double) currentUploaded / state.getTotalChunks() * 100.0;
                                handleListenerProgress(taskKey, state.getTotalChunks(), currentUploaded, progress);
                            }
                        } catch (Exception e) {
                            throw new CompletionException("Failed to upload chunk " + chunkIndex, e);
                        }
                    }, uploadExecutor))
                    .toArray(CompletableFuture[]::new);

            CompletableFuture<Void> allUploads = CompletableFuture.allOf(uploadFutures);

            // Calculate a reasonable timeout
            int chunksToUpload = missingChunks.size();
            long waves = (long) Math.ceil((double) chunksToUpload / this.concurrentUploads);
            long timeoutSeconds = (waves + 2) * this.requestTimeoutSeconds; // Add a 2-request buffer
            timeoutSeconds = Math.max(60, timeoutSeconds); // Minimum 60 seconds

            try {
                allUploads.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new IOException("Chunk uploads timed out after " + timeoutSeconds + " seconds", e);
            }

        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException(UploadErrorClassifier.toUserMessage(cause != null ? cause : e), e);
        } catch (Exception e) {
            throw new IOException(UploadErrorClassifier.toUserMessage(e), e);
        }
    }

    private void applyRateLimit(int dataSize, String traceId) throws InterruptedException {
        if (rateLimiter == null || dataSize <= 0) {
            return;
        }
        rateLimiter.acquire(dataSize, traceId);
    }

    private void handleListenerProgress(String taskKey, int total, int uploaded, double progress) {
        UploadListener listener = listeners.get(taskKey);
        if (listener == null) return;
        try {
            listener.onProgress(total, uploaded, progress);
        } catch (Exception e) {
            logger.warn("UploadListener.onProgress failed: {}", e.getMessage());
        }
    }

    private void handleListenerSuccess(String taskKey, UploadResult result) {
        UploadListener listener = listeners.get(taskKey);
        if (listener == null) return;
        try {
            listener.onComplete(result);
        } catch (Exception e) {
            logger.warn("UploadListener.onComplete failed: {}", e.getMessage());
        }
    }

    private void handleListenerError(String taskKey, String message) {
        UploadListener listener = taskKey != null ? listeners.get(taskKey) : null;
        handleListenerError(listener, message);
    }

    private void handleListenerError(UploadListener listener, String message) {
        if (listener == null) return;
        try {
            listener.onError(message);
        } catch (Exception e) {
            logger.warn("UploadListener.onError failed: {}", e.getMessage());
        }
    }

    private static final Type API_RESPONSE_CHUNK_STATUS = TypeToken.getParameterized(ApiResponse.class, ChunkStatusResponse.class).getType();
    private static final Type API_RESPONSE_CHUNK_INIT = TypeToken.getParameterized(ApiResponse.class, com.cq.agent.dto.ChunkInitResponse.class).getType();
    private static final Type API_RESPONSE_MERGE_RESULT = TypeToken.getParameterized(ApiResponse.class, ChunkMergeResponse.class).getType();
    private static final Type API_RESPONSE_BOOLEAN = TypeToken.getParameterized(ApiResponse.class, Boolean.class).getType();

    private UploadState initOrResumeUpload(File file, String localPath, String remoteTargetPath, String transferId, String traceId) throws IOException, InterruptedException {
        if (transferId != null && !transferId.isEmpty()) {
            logger.debug("[traceId={}] Resuming upload with transferId: {}", traceId, transferId);
            ApiResponse<ChunkStatusResponse> resp = getUploadStatus(transferId, traceId);
            logger.debug("[traceId={}] Upload status retrieved: transferId={}, totalChunks={}, chunkSize={}",
                    traceId, resp.getData().getTransferId(), resp.getData().getTotalChunks(), resp.getData().getChunkSize());
            return new UploadState(resp.getData());
        } else {
            logger.debug("[traceId={}] Initializing new upload: local={}, remote={}, size={}", traceId, localPath, remoteTargetPath, file.length());
            ApiResponse<ChunkInitResponse> resp = initUpload(localPath, remoteTargetPath, file.length(), traceId);
            logger.debug("[traceId={}] Upload initialized successfully: transferId={}, totalChunks={}, chunkSize={}, initTime={}",
                    traceId, resp.getData().getTransferId(), resp.getData().getTotalChunks(), resp.getData().getChunkSize(), resp.getData().getInitTime());
            return new UploadState(resp.getData());
        }
    }

    private UploadState fetchLatestState(String transferId, String traceId) throws IOException, InterruptedException {
        ApiResponse<ChunkStatusResponse> resp = getUploadStatus(transferId, traceId);
        return new UploadState(resp.getData());
    }

    private byte[] readChunk(java.nio.channels.FileChannel channel, int chunkIndex, int chunkSize, long totalSize) throws IOException {
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

    private ApiResponse<ChunkInitResponse> initUpload(String localPath, String remotePath, long totalSize, String traceId) throws IOException, InterruptedException {
        ChunkInitRequest req = new ChunkInitRequest();
        req.setTotalSize(totalSize);

        if (agentConfig != null) {
            req.setSourceAgentId(agentConfig.getAgentId());
            req.setSourceAgentIp(agentConfig.getAgentIp());
            req.setSourceAgentPort(agentConfig.getServerPort());
        }
        File sourceFile = Paths.get(localPath).toFile();
        req.setSourceFileDir(Util.transferToLinuxPath(sourceFile.getParent()));
        req.setSourceFileName(sourceFile.getName());

        File destFile = Paths.get(remotePath).toFile();
        req.setDestFileDir(Util.transferToLinuxPath(destFile.getParent()));
        req.setDestFileName(destFile.getName());
        try {
            URI uri = new URI(agentApiUrl);
            req.setDestAgentIp(uri.getHost());
            req.setDestAgentPort(uri.getPort());
        } catch (java.net.URISyntaxException e) {
            logger.warn("Could not parse agentApiUrl to extract host and port", e);
        }
        return postApi("api/file/chunk/init", req, API_RESPONSE_CHUNK_INIT, traceId);
    }

    private ApiResponse<ChunkStatusResponse> getUploadStatus(String transferId, String traceId) throws IOException, InterruptedException {
        return getApi("api/file/chunk/status?transferId=" + transferId, API_RESPONSE_CHUNK_STATUS, traceId);
    }

    private void uploadChunk(String transferId, int chunkIndex, byte[] data, String localPath, String remotePath, String traceId) throws IOException, InterruptedException {
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
            URI uri = new URI(agentApiUrl);
            req.setDestAgentIp(uri.getHost());
            req.setDestAgentPort(uri.getPort());
        } catch (java.net.URISyntaxException e) {
            logger.warn("[traceId={}] Could not parse agentApiUrl to extract host and port", traceId, e);
        }

        postApi("api/file/chunk/upload", req, TypeToken.getParameterized(ApiResponse.class, Object.class).getType(), traceId);
        logger.debug("[traceId={}] Chunk upload successful: {}", traceId, chunkIndex);
    }

    private UploadResult mergeChunks(String transferId, String traceId) throws IOException, InterruptedException {
        logger.debug("[traceId={}] Merging chunks for transferId: {}", traceId, transferId);
        ChunkMergeRequest req = new ChunkMergeRequest();
        req.setTransferId(transferId);
        ApiResponse<ChunkMergeResponse> response = postApi("api/file/chunk/merge", req, API_RESPONSE_MERGE_RESULT, traceId);

        ChunkMergeResponse data = response.getData();
        if (data == null) throw new IOException("Empty merge result");

        String fullPath = data.getDestFileDir();
        if (fullPath != null && !fullPath.endsWith("/") && !fullPath.endsWith("\\")) {
            fullPath += "/";
        }
        fullPath += data.getDestFileName();

        logger.debug("[traceId={}] Merge successful: path={}, size={}, checksum={}", traceId, fullPath, data.getSize(), data.getChecksum());
        return new UploadResult(fullPath, data.getSize(), data.getChecksum());
    }

    private boolean verifyRemoteFileExists(String remotePath, String traceId) throws IOException, InterruptedException {
        logger.debug("[traceId={}] Verifying remote file exists: {}", traceId, remotePath);
        String encodedPath = java.net.URLEncoder.encode(remotePath, StandardCharsets.UTF_8);
        ApiResponse<Boolean> response = getApi("api/file/exists?path=" + encodedPath, API_RESPONSE_BOOLEAN, traceId);
        if (response == null || !response.isSuccess()) {
            logger.debug("[traceId={}] Verification failed for '{}': {}", traceId, remotePath, response != null ? response.getMsg() : "null");
            return false;
        }
        Boolean data = response.getData();
        logger.debug("[traceId={}] Verification result for '{}': {}", traceId, remotePath, data);
        return Boolean.TRUE.equals(data);
    }

    private <T> ApiResponse<T> postApi(String path, Object body, Type responseType, String traceId) throws IOException, InterruptedException {
        return executeWithRetry(() -> {
            String jsonPayload = body instanceof String ? (String) body : gson.toJson(body);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(agentApiUrl + path))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8));

            // Add traceid header if provided
            if (traceId != null && !traceId.isEmpty()) {
                requestBuilder.header("X-Trace-Id", traceId);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (httpResponse.statusCode() >= 400 && httpResponse.statusCode() != 500) {
                throw new IOException("HTTP request failed with status " + httpResponse.statusCode() + ": " + httpResponse.body());
            }
            ApiResponse<T> obj = gson.fromJson(httpResponse.body(), responseType);
            if (obj == null) throw new IOException("Empty or invalid JSON response");
            return obj;
        });
    }

    private <T> ApiResponse<T> getApi(String path, Type responseType, String traceId) throws IOException, InterruptedException {
        return executeWithRetry(() -> {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(agentApiUrl + path))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .GET();

            // Add traceid header if provided
            if (traceId != null && !traceId.isEmpty()) {
                requestBuilder.header("X-Trace-Id", traceId);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (httpResponse.statusCode() >= 400 && httpResponse.statusCode() != 500) {
                throw new IOException("HTTP request failed with status " + httpResponse.statusCode() + ": " + httpResponse.body());
            }
            ApiResponse<T> obj = gson.fromJson(httpResponse.body(), responseType);
            if (obj == null) throw new IOException("Empty or invalid JSON response");
            return obj;
        });
    }

    private interface RequestAction<T> {
        T execute() throws IOException, InterruptedException;
    }

    private <T> T executeWithRetry(RequestAction<T> action) throws IOException, InterruptedException {
        int attempt = 0;
        Exception last;
        while (true) {
            try {
                T result = action.execute();
                if (result instanceof ApiResponse<?> apiResponse) {
                    if (apiResponse.isSuccess()) {
                        return result;
                    } else {
                        int errorCode = apiResponse.getCode();
                        String errorMsg = apiResponse.getMsg();
                        logger.debug("API request failed: code={}, msg={}", errorCode, errorMsg);

                        if (!UploadErrorClassifier.isRetryableByCode(errorCode)) {
                            logger.error("API request failed (non-retryable): code={}, msg={}", errorCode, errorMsg);
                            throw new IOException("API request failed (non-retryable): " + UploadErrorClassifier.classifyServerError(errorMsg));
                        }
                        logger.warn("API request failed (retryable): code={}, msg={}", errorCode, errorMsg);
                        throw new IOException("API request failed (retryable): " + UploadErrorClassifier.classifyServerError(errorMsg));
                    }
                }
                return result;
            } catch (Exception e) {
                last = e;
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw (InterruptedException) e;
                }
                attempt++;
                if (attempt > maxRetries) {
                    throw new IOException(UploadErrorClassifier.toUserMessage(last), last);
                }
                logger.debug("Request failed (attempt {}/{}), retrying in {}ms: {}",
                        attempt, maxRetries, retryDelayMs, e.getMessage());
                Thread.sleep(retryDelayMs);
            }
        }
    }

    public void shutdown() {
        logger.info("Shutting down AgentUploader...");
        shutdown = true;
        workerExecutor.shutdown();
        uploadExecutor.shutdown();
        try {
            if (!workerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                workerExecutor.shutdownNow();
            }
            if (!uploadExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                uploadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workerExecutor.shutdownNow();
            uploadExecutor.shutdownNow();
        }
        if (rateLimiter != null) {
            rateLimiter.shutdown();
        }
        if (taskQueue != null) {
            taskQueue.close();
        }
        logger.info("AgentUploader shut down");
    }
}