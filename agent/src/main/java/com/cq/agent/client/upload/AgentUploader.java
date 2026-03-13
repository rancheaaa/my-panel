package com.cq.agent.client.upload;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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
    private volatile boolean shutdown;

    private final int maxRetries;
    private final long retryDelayMs;
    private final int connectTimeoutSeconds;
    private final int requestTimeoutSeconds;

    public AgentUploader(String agentApiUrl, int concurrentUploads) {
        this(agentApiUrl, concurrentUploads, DEFAULT_MAX_QUEUE_DEPTH, DEFAULT_WORKER_COUNT, 3, 2000, 10, 60);
    }

    public AgentUploader(String agentApiUrl, int concurrentUploads, int maxQueueDepth, int workerCount,
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

        this.agentApiUrl = agentApiUrl.endsWith("/") ? agentApiUrl : agentApiUrl + "/";
        this.taskQueue = new ArrayBlockingQueue<>(maxQueueDepth);
        this.maxRetries = Math.max(1, maxRetries);
        this.retryDelayMs = Math.max(500, retryDelayMs);
        this.connectTimeoutSeconds = Math.max(5, connectTimeoutSeconds);
        this.requestTimeoutSeconds = Math.max(10, requestTimeoutSeconds);

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(this.connectTimeoutSeconds))
                .build();

        this.uploadExecutor = Executors.newFixedThreadPool(concurrentUploads, r -> {
            Thread t = new Thread(r, "agent-uploader-chunk");
            t.setDaemon(false);
            return t;
        });
        this.workerExecutor = Executors.newFixedThreadPool(workerCount, r -> {
            Thread t = new Thread(r, "agent-uploader-worker");
            t.setDaemon(false);
            return t;
        });
        this.workerCount = workerCount;
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
        if (localFilePath == null || localFilePath.isBlank()) {
            handleListenerError(listener, "localFilePath must not be null or blank");
            return;
        }
        if (remoteTargetPath == null || remoteTargetPath.isBlank()) {
            handleListenerError(listener, "remoteTargetPath must not be null or blank");
            return;
        }

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
            return;
        }
    }

    private void processTask(UploadTask task) {
        String taskKey = Util.md5(task.getLocalFilePath() + ":" + task.getRemoteTargetPath());

        try {
            task.setStatus(UploadTaskStatus.UPLOADING);

            String fileCheck = UploadErrorClassifier.checkLocalFileReadable(task.getLocalFilePath());
            if (fileCheck != null) {
                throw new IOException(fileCheck);
            }

            File file = new File(task.getLocalFilePath());

            UploadState state = initOrResumeUpload(file, task.getRemoteTargetPath(), task.getTransferId());
            if (task.getTransferId() == null || !task.getTransferId().equals(state.getTransferId())) {
                task.setTransferId(state.getTransferId());
            }

            int maxMergeRetries = 3;
            UploadResult finalResult = null;

            for (int attempt = 1; attempt <= maxMergeRetries; attempt++) {
                try {
                    uploadChunks(file, state, taskKey);
                } catch (IOException e) {
                    throw new IOException("Chunk upload failed", e);
                }

                UploadResult mergeAttemptResult = mergeChunks(state.getTransferId());

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

            handleListenerSuccess(taskKey, finalResult);
            logger.info("Upload task completed: {}", taskKey);
            listeners.remove(taskKey);

        } catch (Exception e) {
            String userMsg = UploadErrorClassifier.toUserMessage(e);
            logger.error("Upload task failed: {} - {}", taskKey, userMsg);
            task.setStatus(UploadTaskStatus.FAILED);
            handleListenerError(taskKey, userMsg);
            listeners.remove(taskKey);
        }
    }

    private void uploadChunks(File file, UploadState state, String taskKey) throws IOException {
        AtomicInteger uploadedCount = new AtomicInteger(state.getTotalChunks() - state.getMissingChunks().size());
        UploadListener listener = listeners.get(taskKey);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            CompletableFuture<?>[] uploadFutures = state.getMissingChunks().stream()
                    .map(chunkIndex -> CompletableFuture.runAsync(() -> {
                        try {
                            byte[] chunkData = readChunk(raf, chunkIndex, state.getChunkSize(), state.getTotalSize());
                            uploadChunk(state.getTransferId(), chunkIndex, chunkData);
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

            CompletableFuture.allOf(uploadFutures).join();
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

    private UploadState initOrResumeUpload(File file, String remoteTargetPath, String transferId) throws IOException, InterruptedException {
        JsonObject responseJson;
        if (transferId != null && !transferId.isEmpty()) {
            responseJson = getUploadStatus(transferId);
        } else {
            responseJson = initUpload(remoteTargetPath, file.getName(), file.length());
        }

        if (!responseJson.get("success").getAsBoolean()) {
            String err = responseJson.has("error") ? responseJson.get("error").getAsString() : "Unknown error";
            throw new IOException(UploadErrorClassifier.classifyServerError(err));
        }

        return parseUploadState(responseJson.getAsJsonObject("data"));
    }

    private UploadState fetchLatestState(String transferId) throws IOException, InterruptedException {
        JsonObject responseJson = getUploadStatus(transferId);
        if (!responseJson.get("success").getAsBoolean()) {
            String err = responseJson.has("error") ? responseJson.get("error").getAsString() : "Unknown error";
            throw new IOException(UploadErrorClassifier.classifyServerError(err));
        }
        return parseUploadState(responseJson.getAsJsonObject("data"));
    }

    private UploadState parseUploadState(JsonObject data) {
        Type listType = new TypeToken<List<Integer>>() {}.getType();
        return new UploadState(
                data.get("transferId").getAsString(),
                data.get("totalSize").getAsLong(),
                data.get("totalChunks").getAsInt(),
                data.get("chunkSize").getAsInt(),
                gson.fromJson(data.get("missingChunks"), listType)
        );
    }

    private byte[] readChunk(RandomAccessFile raf, int chunkIndex, int chunkSize, long totalSize) throws IOException {
        long start = (long) chunkIndex * chunkSize;
        long length = Math.min(chunkSize, totalSize - start);
        byte[] data = new byte[(int) length];

        synchronized (raf) {
            raf.seek(start);
            raf.readFully(data);
        }
        return data;
    }

    private JsonObject initUpload(String targetPath, String fileName, long totalSize) throws IOException, InterruptedException {
        JsonObject payload = new JsonObject();
        payload.addProperty("targetPath", targetPath);
        payload.addProperty("fileName", fileName);
        payload.addProperty("totalSize", totalSize);
        return postJson("api/file/chunk/init", payload.toString());
    }

    private JsonObject getUploadStatus(String transferId) throws IOException, InterruptedException {
        return getJson("api/file/chunk/status?transferId=" + transferId);
    }

    private void uploadChunk(String transferId, int chunkIndex, byte[] data) throws IOException, InterruptedException {
        JsonObject payload = new JsonObject();
        payload.addProperty("transferId", transferId);
        payload.addProperty("chunkIndex", chunkIndex);
        payload.addProperty("content", Base64.getEncoder().encodeToString(data));
        payload.addProperty("encoding", "base64");

        JsonObject response = postJson("api/file/chunk/upload", payload.toString());
        if (response == null || !response.has("success") || !response.get("success").getAsBoolean()) {
            String error = (response != null && response.has("error")) ? response.get("error").getAsString() : "Unknown error";
            throw new IOException("Chunk upload failed: " + UploadErrorClassifier.classifyServerError(error));
        }
    }

    private UploadResult mergeChunks(String transferId) throws IOException, InterruptedException {
        JsonObject payload = new JsonObject();
        payload.addProperty("transferId", transferId);

        JsonObject response = postJson("api/file/chunk/merge", payload.toString());
        if (!response.get("success").getAsBoolean()) {
            String err = response.has("error") ? response.get("error").getAsString() : "Unknown error";
            throw new IOException(UploadErrorClassifier.classifyServerError(err));
        }

        JsonObject data = response.getAsJsonObject("data");
        return new UploadResult(
                data.get("path").getAsString(),
                data.get("size").getAsLong(),
                data.has("checksum") ? data.get("checksum").getAsString() : null
        );
    }

    private boolean verifyRemoteFileExists(String remotePath) throws IOException, InterruptedException {
        String encodedPath = java.net.URLEncoder.encode(remotePath, StandardCharsets.UTF_8);
        JsonObject response = getJson("api/file/exists?path=" + encodedPath);

        if (!response.get("success").getAsBoolean()) {
            String error = response.has("error") ? response.get("error").getAsString() : "Unknown API error";
            logger.debug("Verification failed for '{}': {}", remotePath, error);
            return false;
        }
        return response.get("data").getAsBoolean();
    }

    private JsonObject postJson(String path, String jsonPayload) throws IOException, InterruptedException {
        return executeWithRetry(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(agentApiUrl + path))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject obj = gson.fromJson(response.body(), JsonObject.class);
            if (obj == null) {
                throw new IOException("Empty or invalid JSON response");
            }
            return obj;
        });
    }

    private JsonObject getJson(String path) throws IOException, InterruptedException {
        return executeWithRetry(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(agentApiUrl + path))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject obj = gson.fromJson(response.body(), JsonObject.class);
            if (obj == null) {
                throw new IOException("Empty or invalid JSON response");
            }
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
                    throw new IOException(UploadErrorClassifier.toUserMessage(last) + " (重试 " + maxRetries + " 次后失败)", last);
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
