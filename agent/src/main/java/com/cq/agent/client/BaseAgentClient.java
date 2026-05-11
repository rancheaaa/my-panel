package com.cq.agent.client;

import com.cq.agent.client.upload.TransferMetaStore;
import com.cq.agent.client.upload.UploadTaskStatus;
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
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

public abstract class BaseAgentClient<TASK, LISTENER> {

    protected static final Logger logger = LoggerFactory.getLogger(BaseAgentClient.class);

    protected final HttpClient httpClient;
    protected final Gson gson = new Gson();
    
    protected final ConcurrentLinkedQueue<TASK> taskQueue;
    protected final ConcurrentHashMap<String, TASK> inflightTasks = new ConcurrentHashMap<>();
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

    protected TransferMetaStore<TASK> metaStore;

    protected BaseAgentClient(AgentConfig agentConfig, int concurrentThreads,
                            int maxQueueDepth, int workerCount, int maxRetries, long retryDelayMs,
                            int connectTimeoutSeconds, int requestTimeoutSeconds, String metaDirPath,
                            int maxRateKBPerSecond, Class<TASK> taskClass, String operationType) {
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
        this.maxQueueDepth = maxQueueDepth;

        try {
            this.taskQueue = new ConcurrentLinkedQueue<>();

            try {
                Path metaDir = Path.of(metaDirPath);
                if (!java.nio.file.Files.exists(metaDir)) {
                    java.nio.file.Files.createDirectories(metaDir);
                }
                this.metaStore = new TransferMetaStore<>(metaDir, taskClass);
                
                List<TASK> pendingTasks = metaStore.recoverPendingTasks();
                if (!pendingTasks.isEmpty()) {
                    logger.info("恢复 {} 个待处理的 {} 任务", pendingTasks.size(), operationType);
                    taskQueue.addAll(pendingTasks);
                }
            } catch (Exception e) {
                logger.warn("初始化 TransferMetaStore 失败，将使用无持久化模式: {}", e.getMessage());
                this.metaStore = null;
            }

            this.maxRetries = Math.max(1, maxRetries);
            this.retryDelayMs = Math.max(500, retryDelayMs);
            this.connectTimeoutSeconds = Math.max(5, connectTimeoutSeconds);
            this.requestTimeoutSeconds = Math.max(30, requestTimeoutSeconds);

            this.maxRateKBPerSecond = maxRateKBPerSecond;
            if (this.maxRateKBPerSecond > 0) {
                long bytesPerSecond = (long) this.maxRateKBPerSecond * 1024;
                this.rateLimiter = new TrafficRateLimiter(bytesPerSecond);
                logger.info("启用速率限制: {} 最大速率 = {} KB/s", operationType, this.maxRateKBPerSecond);
            } else {
                this.rateLimiter = null;
                logger.info("禁用速率限制 (max{}RateKBPerSecond = 0)", operationType);
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
            
            logger.info("BaseAgentClient 初始化完成: type={}, queueSize={}", operationType, taskQueue.size());
            
        } catch (Exception e) {
            logger.error("初始化 BaseAgentClient 失败: {}", e.getMessage(), e);
            throw new RuntimeException("初始化 BaseAgentClient 失败", e);
        }
    }

    public void init() {
        for (int i = 0; i < workerCount; i++) {
            final int id = i;
            workerExecutor.submit(() -> runWorker(id));
        }
        logger.info("Agent 客户端已初始化: queueSize={}, workers={}", taskQueue.size(), workerCount);
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
                logger.error("Worker {} 错误", id, t);
            }
        }
    }

    protected abstract void processTask(TASK task);
    protected abstract String getTaskKey(TASK task);

    @SuppressWarnings("unchecked")
    protected void updateTaskStatus(TASK task, Object status) {
        try {
            var setStatusMethod = task.getClass().getMethod("setStatus", status.getClass());
            setStatusMethod.invoke(task, status);
        } catch (Exception e) {
            logger.error("更新任务状态失败: {}", e.getMessage());
        }
        
        try {
            var updateTimestampMethod = task.getClass().getMethod("updateTimestamp");
            updateTimestampMethod.invoke(task);
        } catch (Exception e) {
            logger.warn("更新时间戳失败: {}", e.getMessage());
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
            logger.warn("Listener.onProgress 失败: {}", e.getMessage());
        }
    }

    protected void handleListenerBeforeSend(String taskKey, TASK task) {
        LISTENER listener = listenerCache.get(taskKey);
        if (listener == null) return;
        try {
            onListenerBeforeSend(listener, task);
        } catch (Exception e) {
            logger.warn("Listener.onBeforeSend 失败: {}", e.getMessage());
            throw e;
        }
    }

    protected void handleListenerSuccess(String taskKey, TASK result) {
        LISTENER listener = listenerCache.get(taskKey);
        if (listener == null) return;
        try {
            onListenerSuccess(listener, result);
        } catch (Exception e) {
            logger.warn("Listener.onSuccess 失败: {}", e.getMessage());
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
            logger.warn("Listener.onError 失败: {}", e.getMessage());
        }
    }

    protected abstract void onListenerProgress(LISTENER listener, int total, int processed, double progress);
    protected abstract void onListenerBeforeSend(LISTENER listener, TASK task);
    protected abstract void onListenerSuccess(LISTENER listener, TASK result);
    protected abstract void onListenerError(LISTENER listener, String message);

    protected LISTENER createListenerInstance(String className, Class<LISTENER> listenerClass) {
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (listenerClass.isInstance(instance)) {
                return listenerClass.cast(instance);
            } else {
                logger.error("类 {} 没有实现 {} 接口", className, listenerClass.getSimpleName());
                return null;
            }
        } catch (ClassNotFoundException e) {
            logger.error("未找到 Listener 类: {}", className, e);
            return null;
        } catch (NoSuchMethodException e) {
            logger.error("Listener 类 {} 没有默认构造函数", className, e);
            return null;
        } catch (Exception e) {
            logger.error("创建 Listener 实例失败: {}", className, e);
            return null;
        }
    }

    protected <T> ApiResponse<T> postApi(String remoteAgentApiUrl, String endpoint, Object requestBody, Type responseType, String traceId) throws IOException, InterruptedException {
        try {
            String url = remoteAgentApiUrl + endpoint;
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
            logger.error("[traceId={}] POST {} 失败", traceId, endpoint, e);
            throw new IOException("API 请求失败: " + endpoint, e);
        }
    }

    protected <T> ApiResponse<T> getApi(String remoteAgentApiUrl, String endpoint, Type responseType, String traceId) throws IOException, InterruptedException {
        try {
            String url = remoteAgentApiUrl + endpoint;
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
            logger.error("[traceId={}] GET {} 失败", traceId, endpoint, e);
            throw new IOException("API 请求失败: " + endpoint, e);
        }
    }

    public void shutdown() {
        logger.info("正在关闭 Agent 客户端...");
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
        logger.info("Agent 客户端已关闭");
    }
}
