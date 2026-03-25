package com.cq.agent.client;

import com.cq.agent.client.upload.PersistentMap;
import com.cq.agent.client.upload.PersistentQueue;
import com.cq.agent.client.upload.Util;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.dto.ApiResponse;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;

public abstract class BaseAgentClient<TASK, LISTENER> {

    protected static final Logger logger = LoggerFactory.getLogger(BaseAgentClient.class);

    protected final String agentApiUrl;
    protected final HttpClient httpClient;
    protected final Gson gson = new Gson();
    protected final PersistentQueue<TASK> taskQueue;
    protected final PersistentMap<String, TASK> taskInflightMap;
    protected final ConcurrentHashMap<String, LISTENER> listenerCache = new ConcurrentHashMap<>();
    protected final ExecutorService chunkExecutor;
    protected final ExecutorService workerExecutor;
    protected final int workerCount;
    protected final int concurrentThreads;
    protected volatile boolean shutdown;

    protected final AgentConfig agentConfig;

    protected final int maxRetries;
    protected final long retryDelayMs;
    protected final int connectTimeoutSeconds;
    protected final int requestTimeoutSeconds;

    protected final int maxRateKBPerSecond;
    protected final TrafficRateLimiter rateLimiter;
    protected final int maxQueueDepth;

    protected BaseAgentClient(AgentConfig agentConfig, String agentApiUrl, int concurrentThreads,
                            int maxQueueDepth, int workerCount, int maxRetries, long retryDelayMs,
                            int connectTimeoutSeconds, int requestTimeoutSeconds, String queueDbPath, String mapDbPath,
                            int maxRateKBPerSecond, Class<TASK> taskClass, String operationType) {
        if (agentApiUrl == null || agentApiUrl.isBlank()) {
            throw new IllegalArgumentException("agentApiUrl must not be null or blank");
        }
        if (concurrentThreads < 1 || concurrentThreads > 64) {
            throw new IllegalArgumentException("concurrentThreads must be between 1 and 64");
        }
        if (maxQueueDepth < 1 || maxQueueDepth > 100_000) {
            throw new IllegalArgumentException("maxQueueDepth must be between 1 and 100000");
        }
        if (workerCount < 1 || workerCount > 32) {
            throw new IllegalArgumentException("workerCount must be between 1 and 32");
        }

        this.agentConfig = agentConfig;
        this.agentApiUrl = agentApiUrl.endsWith("/") ? agentApiUrl : agentApiUrl + "/";
        this.maxQueueDepth = maxQueueDepth;

        try {

            this.taskQueue = new PersistentQueue<>(Util.resolveAndCreatePathIfAbsent(agentConfig.getFileBaseDirectory(), queueDbPath), operationType + "QueueDb", taskClass);
            this.taskInflightMap = new PersistentMap<>(Util.resolveAndCreatePathIfAbsent(agentConfig.getFileBaseDirectory(), mapDbPath), operationType + "MapDb", String.class, taskClass);
            logger.info("Persistent queue initialized at: {}", queueDbPath);
            logger.info("Queue size on startup: {}", taskQueue.size());
            if (!taskQueue.isEmpty()) {
                logger.info("Resuming {} pending {} tasks from previous session", taskQueue.size(), operationType);
            }

            this.maxRetries = Math.max(1, maxRetries);
            this.retryDelayMs = Math.max(500, retryDelayMs);
            this.connectTimeoutSeconds = Math.max(5, connectTimeoutSeconds);
            this.requestTimeoutSeconds = Math.max(30, requestTimeoutSeconds);

            this.maxRateKBPerSecond = maxRateKBPerSecond;
            if (this.maxRateKBPerSecond > 0) {
                long bytesPerSecond = (long) this.maxRateKBPerSecond * 1024;
                this.rateLimiter = new TrafficRateLimiter(bytesPerSecond);
                logger.info("Rate limiting enabled: max {} rate = {} KB/s", operationType, this.maxRateKBPerSecond);
            } else {
                this.rateLimiter = null;
                logger.info("Rate limiting disabled (max{}RateKBPerSecond = 0)", operationType);
            }

            this.httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(this.connectTimeoutSeconds))
                    .build();

            this.chunkExecutor = new ThreadPoolExecutor(
                    concurrentThreads,
                    concurrentThreads,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(),
                    r -> {
                        Thread t = new Thread(r, "agent-" + operationType + "-chunk");
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
                        Thread t = new Thread(r, "agent-" + operationType + "-worker");
                        t.setDaemon(false);
                        return t;
                    }
            );
            this.workerCount = workerCount;
            this.concurrentThreads = concurrentThreads;
        } catch (Exception e) {
            logger.error("Failed to initialize BaseAgentClient: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize BaseAgentClient", e);
        }
    }

    public void init() {
        for (int i = 0; i < workerCount; i++) {
            final int id = i;
            workerExecutor.submit(() -> runWorker(id));
        }
        logger.info("Agent client initialized, queue size={}, workers={}", taskQueue.size(), workerCount);
    }

    protected void runWorker(int id) {
        while (!shutdown) {
            TASK task = null;
            try {
                task = taskQueue.poll();
                if (task == null) {
                    TimeUnit.SECONDS.sleep(1);
                    continue;
                }
                processTask(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                if (task != null) {
                    String taskKey = getTaskKey(task);
                    handleListenerError(taskKey, t.getMessage());
                    listenerCache.remove(taskKey);
                }
                logger.error("Worker {} error", id, t);
            }
        }
    }

    protected abstract void processTask(TASK task);
    protected abstract String getTaskKey(TASK task);

    protected void applyRateLimit(int dataSize, String traceId) throws InterruptedException {
        if (rateLimiter == null || dataSize <= 0) {
            return;
        }
        rateLimiter.acquire(dataSize, traceId);
    }

    protected void handleListenerProgress(String taskKey, int total, int processed, double progress) {
        LISTENER listener = listenerCache.get(taskKey);
        if (listener == null) return;
        try {
            onListenerProgress(listener, total, processed, progress);
        } catch (Exception e) {
            logger.warn("Listener.onProgress failed: {}", e.getMessage());
        }
    }

    protected void handleListenerSuccess(String taskKey, TASK result) {
        LISTENER listener = listenerCache.get(taskKey);
        if (listener == null) return;
        try {
            onListenerSuccess(listener, result);
        } catch (Exception e) {
            logger.warn("Listener.onSuccess failed: {}", e.getMessage());
        }
    }

    protected void handleListenerError(String taskKey, String message) {
        LISTENER listener = taskKey != null ? listenerCache.get(taskKey) : null;
        handleListenerError(listener, message);
    }

    protected void handleListenerError(LISTENER listener, String message) {
        if (listener == null) return;
        try {
            onListenerError(listener, message);
        } catch (Exception e) {
            logger.warn("Listener.onError failed: {}", e.getMessage());
        }
    }

    protected abstract void onListenerProgress(LISTENER listener, int total, int processed, double progress);
    protected abstract void onListenerSuccess(LISTENER listener, TASK result);
    protected abstract void onListenerError(LISTENER listener, String message);

    protected LISTENER createListenerInstance(String className, Class<LISTENER> listenerClass) {
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (listenerClass.isInstance(instance)) {
                return listenerClass.cast(instance);
            } else {
                logger.error("Class {} does not implement {} interface", className, listenerClass.getSimpleName());
                return null;
            }
        } catch (ClassNotFoundException e) {
            logger.error("Listener class not found: {}", className, e);
            return null;
        } catch (NoSuchMethodException e) {
            logger.error("Listener class {} has no default constructor", className, e);
            return null;
        } catch (Exception e) {
            logger.error("Failed to create listener instance: {}", className, e);
            return null;
        }
    }

    protected <T> ApiResponse<T> postApi(String endpoint, Object requestBody, Type responseType, String traceId) throws IOException, InterruptedException {
        try {
            String url = agentApiUrl + endpoint;
            String jsonBody = gson.toJson(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("X-Trace-Id", traceId)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            logger.debug("[traceId={}] POST {} - Request: {}, body length {}", traceId, endpoint, request, jsonBody.length());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();
            ApiResponse<T> apiResponse = gson.fromJson(responseBody, responseType);

            logger.debug("[traceId={}] POST {} - Status: {}, Response: {}", traceId, endpoint, response.statusCode(), apiResponse);

            return apiResponse;
        } catch (Exception e) {
            logger.error("[traceId={}] POST {} failed", traceId, endpoint, e);
            throw new IOException("API request failed: " + endpoint, e);
        }
    }

    protected <T> ApiResponse<T> getApi(String endpoint, Type responseType, String traceId) throws IOException, InterruptedException {
        try {
            String url = agentApiUrl + endpoint;
            if (!url.contains("traceId")) {
                url += (url.contains("?") ? "&" : "?") + "traceId=" + traceId;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("X-Trace-Id", traceId)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();
            ApiResponse<T> apiResponse = gson.fromJson(responseBody, responseType);

            logger.debug("[traceId={}] GET {} - Status: {}, Response: {}", traceId, endpoint, response.statusCode(), apiResponse);

            return apiResponse;
        } catch (Exception e) {
            logger.error("[traceId={}] GET {} failed", traceId, endpoint, e);
            throw new IOException("API request failed: " + endpoint, e);
        }
    }

    public void shutdown() {
        logger.info("Shutting down agent client...");
        shutdown = true;
        workerExecutor.shutdown();
        chunkExecutor.shutdown();
        try {
            if (!workerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                workerExecutor.shutdownNow();
            }
            if (!chunkExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                chunkExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workerExecutor.shutdownNow();
            chunkExecutor.shutdownNow();
        }
        if (rateLimiter != null) {
            rateLimiter.shutdown();
        }
        if (taskQueue != null) {
            taskQueue.close();
        }
        if (taskInflightMap != null) {
            taskInflightMap.close();
        }
        logger.info("Agent client shut down");
    }
}