package com.cq.agent.client.upload;

import com.cq.agent.config.AgentConfig;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.ChunkInitRequest;
import com.cq.agent.dto.ChunkMergeRequest;
import com.cq.agent.dto.ChunkStatusData;
import com.cq.agent.dto.MergeResultData;
import com.cq.agent.dto.ChunkUploadRequest;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.MDC;

/**
 * Agent upload client for chunked file transfer.
 * Uses a bounded blocking queue to limit memory; tasks are in-memory only.
 */
public class AgentUploader {

    private static final Logger logger = LoggerFactory.getLogger(AgentUploader.class);
    private static final int DEFAULT_MAX_QUEUE_DEPTH = 500;
    private static final int DEFAULT_WORKER_COUNT = 4;

    private final String agentApiUrl;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final BlockingQueue<UploadTask> taskQueue;
    private final ConcurrentHashMap.KeySetView<String, Boolean> taskKeysInFlight = ConcurrentHashMap.newKeySet();
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
        this.taskQueue = new ArrayBlockingQueue<>(maxQueueDepth);
        this.maxRetries = Math.max(1, maxRetries);
        this.retryDelayMs = Math.max(500, retryDelayMs);
        this.connectTimeoutSeconds = Math.max(5, connectTimeoutSeconds);
        this.requestTimeoutSeconds = Math.max(30, requestTimeoutSeconds);

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
    }

    public void init() {
        for (int i = 0; i < workerCount; i++) {
            final int id = i;
            workerExecutor.submit(() -> runWorker(id));
        }
        logger.info("AgentUploader initialized, queue depth={}, workers={}", taskQueue.remainingCapacity() + taskQueue.size(), workerCount);
    }

    private void runWorker(int id) {
        while (!shutdown) {
            UploadTask task = null;
            try {
                task = taskQueue.poll(1, TimeUnit.SECONDS);
                if (task == null) continue;

                String taskKey = Util.md5(task.getLocalFilePath() + ":" + task.getRemoteTargetPath());
                try {
                    processTask(task);
                } finally {
                    taskKeysInFlight.remove(taskKey);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                if (task != null) {
                    String taskKey = Util.md5(task.getLocalFilePath() + ":" + task.getRemoteTargetPath());
                    taskKeysInFlight.remove(taskKey);
                    handleListenerError(taskKey, UploadErrorClassifier.toUserMessage(t instanceof Exception ? (Exception) t : new Exception(t)));
                    listeners.remove(taskKey);
                }
                logger.error("Worker {} error", id, t);
            }
        }
    }

    public void uploadFile(String localFilePath, String remoteTargetPath, UploadListener listener) {
        // Generate traceid
        String traceid = java.util.UUID.randomUUID().toString();
        MDC.put("traceid", traceid);
        
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
//        if (!new File(remoteTargetPath).isAbsolute()) {
//            handleListenerError(listener, "remoteTargetPath must be an absolute path: " + remoteTargetPath);
//            return;
//        }

            String taskKey = Util.md5(localFilePath + ":" + remoteTargetPath);

            if (listener != null) {
                listeners.put(taskKey, listener);
            }

            if (!taskKeysInFlight.add(taskKey)) {
                logger.debug("Task already in queue or processing: {}", taskKey);
                return;
            }

            UploadTask task = new UploadTask(localFilePath, remoteTargetPath);
            if (!taskQueue.offer(task)) {
                taskKeysInFlight.remove(taskKey);
                handleListenerError(taskKey, "Upload queue is full, please retry later");
            }
        } finally {
            MDC.clear();
        }
    }

    private void processTask(UploadTask task) {
        String taskKey = Util.md5(task.getLocalFilePath() + ":" + task.getRemoteTargetPath());
        // Generate traceid for task processing
        String traceid = java.util.UUID.randomUUID().toString();
        MDC.put("traceid", traceid);
        
        try {
            task.setStatus(UploadTaskStatus.UPLOADING);

            String fileCheck = Util.checkLocalFileReadable(task.getLocalFilePath());
            if (fileCheck != null) {
                throw new IOException(fileCheck);
            }

            File file = new File(task.getLocalFilePath());

            int maxMergeRetries = 3;
            UploadResult finalResult = null;

            UploadState state;
            state = initOrResumeUpload(file, task.getLocalFilePath(), task.getRemoteTargetPath(), task.getTransferId());
            if (task.getTransferId() == null || !task.getTransferId().equals(state.getTransferId())) {
                task.setTransferId(state.getTransferId());
            }
            for (int attempt = 1; attempt <= maxMergeRetries; attempt++) {
                try {
                    uploadChunks(file, state, taskKey, task.getLocalFilePath(), task.getRemoteTargetPath());
                } catch (IOException e) {
                    throw new IOException("Chunk upload failed", e);
                }

                UploadResult mergeAttemptResult = null;
                try {
                    mergeAttemptResult = mergeChunks(state.getTransferId());
                } catch (IOException e) {
                    if (attempt < maxMergeRetries && (e.getMessage().contains("size mismatch") || e.getMessage().contains("2007"))) {
                        logger.warn("Merge failed due to size mismatch (attempt {}/{}), re-syncing and retrying", attempt, maxMergeRetries);
                        state = fetchLatestState(state.getTransferId());
                        continue;
                    }
                }
                if (mergeAttemptResult == null) {
                    logger.warn("Merge failed (attempt {}/{}), re-syncing and retrying", attempt, maxMergeRetries);
                    state = fetchLatestState(state.getTransferId());
                    continue;
                }
                if (verifyRemoteFileExists(mergeAttemptResult.getPath())) {
                    finalResult = new UploadResult(mergeAttemptResult.getPath(), mergeAttemptResult.getSize(),
                            mergeAttemptResult.getChecksum(), UploadCompletionState.VERIFIED);
                    break;
                }

                if (attempt < maxMergeRetries) {
                    logger.warn("Verification failed after merge (attempt {}/{}), re-syncing", attempt, maxMergeRetries);
                    state = fetchLatestState(state.getTransferId());
                } else {
                    throw new IOException("Verification failed after " + maxMergeRetries + " attempts");
                }
            }

            if (finalResult != null) {
                task.setStatus(UploadTaskStatus.UPLOAD_SUCCESS);
                handleListenerSuccess(taskKey, finalResult);
            } else {
                throw new IOException("Upload failed after all retries, but no result was produced.");
            }
            logger.info("Upload task completed: {}", taskKey);
            listeners.remove(taskKey);

        } catch (Exception e) {
            String userMsg = UploadErrorClassifier.toUserMessage(e);
            logger.error("Upload task failed: {} - {}", taskKey, userMsg);
            task.setStatus(UploadTaskStatus.FAILED);
            handleListenerError(taskKey, userMsg);
            listeners.remove(taskKey);
        } finally {
            MDC.clear();
        }
    }

    private void uploadChunks(File file, UploadState state, String taskKey, String localPath, String remotePath) throws IOException {
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
                            uploadChunk(state.getTransferId(), chunkIndex, chunkData, localPath, remotePath);
                            int currentUploaded = uploadedCount.incrementAndGet();
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

    private static final Type API_RESPONSE_CHUNK_STATUS = TypeToken.getParameterized(ApiResponse.class, ChunkStatusData.class).getType();
    private static final Type API_RESPONSE_MERGE_RESULT = TypeToken.getParameterized(ApiResponse.class, MergeResultData.class).getType();
    private static final Type API_RESPONSE_BOOLEAN = TypeToken.getParameterized(ApiResponse.class, Boolean.class).getType();

    private UploadState initOrResumeUpload(File file, String localPath, String remoteTargetPath, String transferId) throws IOException, InterruptedException {
        ApiResponse<ChunkStatusData> resp;
        if (transferId != null && !transferId.isEmpty()) {
            logger.debug("Resuming upload with transferId: {}", transferId);
            resp = getUploadStatus(transferId);
        } else {
            logger.debug("Initializing new upload: local={}, remote={}, size={}", localPath, remoteTargetPath, file.length());
            resp = initUpload(localPath, remoteTargetPath, file.length());
        }
        if (!resp.isSuccess()) {
            logger.debug("Upload initialization failed: {}", resp.getMsg());
            throw new IOException(UploadErrorClassifier.classifyServerError(resp.getMsg()));
        }
        logger.debug("Upload initialized successfully: transferId={}, totalChunks={}, chunkSize={}", 
                resp.getData().getTransferId(), resp.getData().getTotalChunks(), resp.getData().getChunkSize());
        return toUploadState(resp.getData());
    }

    private UploadState fetchLatestState(String transferId) throws IOException, InterruptedException {
        ApiResponse<ChunkStatusData> resp = getUploadStatus(transferId);
        if (!resp.isSuccess()) {
            throw new IOException(UploadErrorClassifier.classifyServerError(resp.getMsg()));
        }
        return toUploadState(resp.getData());
    }

    private static UploadState toUploadState(ChunkStatusData data) throws IOException {
        if (data == null) throw new IOException("Empty chunk status data");
        List<Integer> missing = data.getMissingChunks();
        if (missing == null) {
            missing = new java.util.ArrayList<>();
            for (int i = 0; i < data.getTotalChunks(); i++) {
                missing.add(i);
            }
        } else {
            // Ensure elements are Integers (defensive against Gson potential Double issues in generic maps)
            List<Integer> intList = new java.util.ArrayList<>();
            for (Object o : missing) {
                if (o instanceof Number) {
                    intList.add(((Number) o).intValue());
                } else if (o instanceof String) {
                    intList.add(Integer.parseInt((String) o));
                }
            }
            missing = intList;
        }
        return new UploadState(
                data.getTransferId(),
                data.getTotalSize(),
                data.getTotalChunks(),
                data.getChunkSize(),
                missing
        );
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

    private ApiResponse<ChunkStatusData> initUpload(String localPath, String remotePath, long totalSize) throws IOException, InterruptedException {
        ChunkInitRequest req = new ChunkInitRequest();
        req.setTotalSize(totalSize);

        // Populate source and destination info
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
        // Assuming the destination is the agent we are connected to
        try {
            URI uri = new URI(agentApiUrl);
            req.setDestAgentIp(uri.getHost());
            req.setDestAgentPort(uri.getPort());
        } catch (java.net.URISyntaxException e) {
            logger.warn("Could not parse agentApiUrl to extract host and port", e);
        }
        return postApi("api/file/chunk/init", req, API_RESPONSE_CHUNK_STATUS);
    }

    private ApiResponse<ChunkStatusData> getUploadStatus(String transferId) throws IOException, InterruptedException {
        return getApi("api/file/chunk/status?transferId=" + transferId, API_RESPONSE_CHUNK_STATUS);
    }

    private void uploadChunk(String transferId, int chunkIndex, byte[] data, String localPath, String remotePath) throws IOException, InterruptedException {
        logger.debug("Uploading chunk {} for transferId: {}", chunkIndex, transferId);
        ChunkUploadRequest req = new ChunkUploadRequest();
        req.setTransferId(transferId);
        req.setChunkIndex(chunkIndex);
        req.setContent(Base64.getEncoder().encodeToString(data));
        req.setEncoding("base64");

        // Populate source and destination info
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

        // Assuming the destination is the agent we are connected to
        try {
            URI uri = new URI(agentApiUrl);
            req.setDestAgentIp(uri.getHost());
            req.setDestAgentPort(uri.getPort());
        } catch (java.net.URISyntaxException e) {
            logger.warn("Could not parse agentApiUrl to extract host and port", e);
        }


        ApiResponse<?> response = postApi("api/file/chunk/upload", req, TypeToken.getParameterized(ApiResponse.class, Object.class).getType());
        if (response == null || !response.isSuccess()) {
            String error = response != null ? response.getMsg() : "Unknown error";
            logger.debug("Chunk upload failed: {} - {}", chunkIndex, error);
            throw new IOException("Chunk upload failed: " + UploadErrorClassifier.classifyServerError(error));
        }
        logger.debug("Chunk upload successful: {}", chunkIndex);
    }

    private UploadResult mergeChunks(String transferId) throws IOException, InterruptedException {
        logger.debug("Merging chunks for transferId: {}", transferId);
        ChunkMergeRequest req = new ChunkMergeRequest();
        req.setTransferId(transferId);
        ApiResponse<MergeResultData> response = postApi("api/file/chunk/merge", req, API_RESPONSE_MERGE_RESULT);

        if (response != null && response.isSuccess()) {
            MergeResultData data = response.getData();
            if (data == null) throw new IOException("Empty merge result");
            
            // Reconstruct the full path from dir and name
            String fullPath = data.getDestFileDir();
            if (fullPath != null && !fullPath.endsWith("/") && !fullPath.endsWith("\\")) {
                fullPath += "/";
            }
            fullPath += data.getDestFileName();
            
            logger.debug("Merge successful: path={}, size={}, checksum={}", fullPath, data.getSize(), data.getChecksum());
            return new UploadResult(fullPath, data.getSize(), data.getChecksum());
        }

        // Check for merge size mismatch error (ApiCode.MERGE_SIZE_MISMATCH = 2007)
        if (response != null && (response.getCode() == 2007 || (response.getMsg() != null && response.getMsg().contains("Merged file size does not match")))) {
            logger.debug("Merge failed due to size mismatch: {}", response.getMsg());
            throw new IOException("Merge failed due to size mismatch: " + response.getMsg());
        } else {
            String error = response != null ? response.getMsg() : "Unknown error";
            logger.debug("Merge failed: {}", error);
            throw new IOException(UploadErrorClassifier.classifyServerError(error));
        }
    }

    private boolean verifyRemoteFileExists(String remotePath) throws IOException, InterruptedException {
        logger.debug("Verifying remote file exists: {}", remotePath);
        String encodedPath = java.net.URLEncoder.encode(remotePath, StandardCharsets.UTF_8);
        ApiResponse<Boolean> response = getApi("api/file/exists?path=" + encodedPath, API_RESPONSE_BOOLEAN);
        if (response == null || !response.isSuccess()) {
            logger.debug("Verification failed for '{}': {}", remotePath, response != null ? response.getMsg() : "null");
            return false;
        }
        Boolean data = response.getData();
        logger.debug("Verification result for '{}': {}", remotePath, data);
        return Boolean.TRUE.equals(data);
    }

    private <T> ApiResponse<T> postApi(String path, Object body, Type responseType) throws IOException, InterruptedException {
        return executeWithRetry(() -> {
            String jsonPayload = body instanceof String ? (String) body : gson.toJson(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(agentApiUrl + path))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (httpResponse.statusCode() >= 400 && httpResponse.statusCode() != 500) {
                throw new IOException("HTTP request failed with status " + httpResponse.statusCode() + ": " + httpResponse.body());
            }
            ApiResponse<T> obj = gson.fromJson(httpResponse.body(), responseType);
            if (obj == null) throw new IOException("Empty or invalid JSON response");
            return obj;
        });
    }

    private <T> ApiResponse<T> getApi(String path, Type responseType) throws IOException, InterruptedException {
        return executeWithRetry(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(agentApiUrl + path))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .GET()
                    .build();
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
        Exception last = null;
        while (true) {
            try {
                return action.execute();
            } catch (Exception e) {
                last = e;
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw (InterruptedException) e;
                }
                if (UploadErrorClassifier.isNonRetryable(e)) {
                    throw new IOException(UploadErrorClassifier.toUserMessage(e), last);
                }
                attempt++;
                if (attempt > maxRetries) {
                    throw new IOException(UploadErrorClassifier.toUserMessage(last) + " (ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â©ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¨ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ " + maxRetries + " ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â½ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â±ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¨ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â´ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥)", last);
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
        logger.info("AgentUploader shut down");
    }
}