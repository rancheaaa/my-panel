package com.cq.agent.client.download;

import com.cq.agent.client.upload.PersistentMap;
import com.cq.agent.client.upload.PersistentQueue;
import com.cq.agent.client.upload.UploadRateLimiter;
import com.cq.agent.client.upload.Util;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.dto.*;
import com.google.gson.Gson;
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
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgentDownloader {

    private static final Logger logger = LoggerFactory.getLogger(AgentDownloader.class);
    private static final int DEFAULT_MAX_QUEUE_DEPTH = 500;
    private static final int DEFAULT_WORKER_COUNT = 4;

    private final String agentApiUrl;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final PersistentQueue<DownloadTask> taskQueue;
    private final PersistentMap<String, DownloadTask> taskInflightMap;
    private final ConcurrentHashMap<String, DownloadListener> listenerCache = new ConcurrentHashMap<>();
    private final ExecutorService downloadExecutor;
    private final ExecutorService workerExecutor;
    private final int workerCount;
    private final int concurrentDownloads;
    private volatile boolean shutdown;

    private final AgentConfig agentConfig;

    private final int maxRetries;
    private final long retryDelayMs;
    private final int connectTimeoutSeconds;
    private final int requestTimeoutSeconds;

    private final int maxDownloadRateKBPerSecond;
    private final UploadRateLimiter rateLimiter;

    public AgentDownloader(AgentConfig agentConfig, String agentApiUrl, int concurrentDownloads) {
        this(agentConfig, agentApiUrl, concurrentDownloads, DEFAULT_MAX_QUEUE_DEPTH, DEFAULT_WORKER_COUNT, 3, 2000, 10, 60);
    }

    public AgentDownloader(AgentConfig agentConfig, String agentApiUrl, int concurrentDownloads, int maxQueueDepth, int workerCount,
                           int maxRetries, long retryDelayMs, int connectTimeoutSeconds, int requestTimeoutSeconds) {
        if (agentApiUrl == null || agentApiUrl.isBlank()) {
            throw new IllegalArgumentException("agentApiUrl must not be null or blank");
        }
        if (concurrentDownloads < 1 || concurrentDownloads > 64) {
            throw new IllegalArgumentException("concurrentDownloads must be between 1 and 64");
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
            this.taskQueue = new PersistentQueue<>(agentConfig.getDownloadQueueDbPath(), "download-tasks", DownloadTask.class);
            this.taskInflightMap = new PersistentMap<>(agentConfig.getDownloadMapDbPath(), "download-inflight", String.class, DownloadTask.class);
            logger.info("Persistent queue initialized at: {}", agentConfig.getDownloadQueueDbPath());
            logger.info("Queue size on startup: {}", taskQueue.size());
            if (!taskQueue.isEmpty()) {
                logger.info("Resuming {} pending download tasks from previous session", taskQueue.size());
            }
            this.maxRetries = Math.max(1, maxRetries);
            this.retryDelayMs = Math.max(500, retryDelayMs);
            this.connectTimeoutSeconds = Math.max(5, connectTimeoutSeconds);
            this.requestTimeoutSeconds = Math.max(30, requestTimeoutSeconds);

            this.maxDownloadRateKBPerSecond = agentConfig.getMaxDownloadRateKBPerSecond();
            if (this.maxDownloadRateKBPerSecond > 0) {
                long bytesPerSecond = (long) this.maxDownloadRateKBPerSecond * 1024;
                this.rateLimiter = new UploadRateLimiter(bytesPerSecond);
                logger.info("Rate limiting enabled: max download rate = {} KB/s", this.maxDownloadRateKBPerSecond);
            } else {
                this.rateLimiter = null;
                logger.info("Rate limiting disabled (maxDownloadRateKBPerSecond = 0)");
            }

            this.httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(this.connectTimeoutSeconds))
                    .build();

            this.downloadExecutor = new ThreadPoolExecutor(
                    concurrentDownloads,
                    concurrentDownloads,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(),
                    r -> {
                        Thread t = new Thread(r, "agent-downloader-chunk");
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
                        Thread t = new Thread(r, "agent-downloader-worker");
                        t.setDaemon(false);
                        return t;
                    }
            );
            this.workerCount = workerCount;
            this.concurrentDownloads = concurrentDownloads;
        } catch (Exception e) {
            logger.error("Failed to initialize AgentDownloader: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize AgentDownloader", e);
        }
    }

    public void init() {
        for (int i = 0; i < workerCount; i++) {
            final int id = i;
            workerExecutor.submit(() -> runWorker(id));
        }
        logger.info("AgentDownloader initialized, queue size={}, workers={}", taskQueue.size(), workerCount);
    }

    private void runWorker(int id) {
        while (!shutdown) {
            DownloadTask task = null;
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
                    String taskKey = Util.md5(task.getRemoteFilePath() + ":" + task.getLocalFilePath());
                    handleListenerError(taskKey, t.getMessage());
                    listenerCache.remove(taskKey);
                }
                logger.error("Worker {} error", id, t);
            }
        }
    }

    public boolean downloadFile(String remoteFilePath, String localFilePath, DownloadListener listener) {
        String traceId = UUID.randomUUID().toString().replace("-", "");

        try {
            if (remoteFilePath == null || remoteFilePath.isBlank()) {
                handleListenerError(listener, "remoteFilePath must not be null or blank");
                return false;
            }
            if (localFilePath == null || localFilePath.isBlank()) {
                handleListenerError(listener, "localFilePath must not be null or blank");
                return false;
            }

            File localFile = new File(localFilePath);
            File parentDir = localFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            String taskKey = Util.md5(remoteFilePath + ":" + localFilePath);

            if (listener != null) {
                listenerCache.put(taskKey, listener);
            }

            long fileSize = getRemoteFileSize(remoteFilePath, traceId);
            if (fileSize <= 0) {
                handleListenerError(listener, "Failed to get remote file size");
                return false;
            }

            DownloadTask task = new DownloadTask(remoteFilePath, localFilePath, fileSize);
            task.setTransferId(UUID.randomUUID().toString().replace("-", ""));
            task.setTraceId(traceId);
            task.setEnqueuedTime(Util.currentTime());
            task.setListenerClassName(listener != null ? listener.getClass().getName() : null);
            task.updateTimestamp();

            if (!this.taskInflightMap.containsKey(task.getTransferId())) {
                this.taskInflightMap.put(task.getTransferId(), task);
            }

            return taskQueue.offer(task);
        } catch (Exception e) {
            logger.error("download file with error!", e);
            handleListenerError(listener, "download file with error!");
            return false;
        }
    }

    private void processTask(DownloadTask task) {
        String taskKey = Util.md5(task.getRemoteFilePath() + ":" + task.getLocalFilePath());
        String traceId = task.getTraceId();

        try {
            task.setStatus(DownloadTaskStatus.INIT_DOWNLOADING);
            task.updateTimestamp();
            task.incrementRetryCount();
            this.taskInflightMap.put(task.getTransferId(), task);

            String listenerClassName = task.getListenerClassName();
            if (listenerClassName != null && !listenerCache.containsKey(taskKey)) {
                DownloadListener listener = createListenerInstance(listenerClassName);
                if (listener != null) {
                    listenerCache.put(taskKey, listener);
                }
            }

            File localFile = new File(task.getLocalFilePath());
            File parentDir = localFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                final boolean res = parentDir.mkdirs();
                if(!res) {
                    logger.error("[traceId={}] Failed to create parent directory: {}", traceId, parentDir.getAbsolutePath());
                    throw new IOException("Failed to create parent directory: " + parentDir.getAbsolutePath());
                }
            }
            final String tmpPath = parentDir.toPath().resolve("." + UUID.randomUUID().toString().replace("-", ""))
                    .normalize().toAbsolutePath().toString();
            task.setTmpLocalFilePath(tmpPath);

            int chunkSize;
            int totalChunks;

            ApiResponse<ChunkDownloadInfoResponse> chunkInitResponse = getDownloadInfo(task);
            logger.debug("[traceId={}] Download initialized: transferId={}, totalChunks={}, chunkSize={}",
                    traceId, chunkInitResponse.getData().getTransferId(),
                    chunkInitResponse.getData().getTotalChunks(),
                    chunkInitResponse.getData().getChunkSize());

            chunkSize = chunkInitResponse.getData().getChunkSize();
            totalChunks = chunkInitResponse.getData().getTotalChunks();

            task.setInitDownloadEndTime(Util.currentTime());
            task.setStatus(DownloadTaskStatus.INIT_DOWNLOAD_COMPLETED);
            task.updateTimestamp();

            task.setChunkSize(chunkSize);
            task.setTotalChunks(totalChunks);
            this.taskInflightMap.put(task.getTransferId(), task);

            try {
                task.setStatus(DownloadTaskStatus.DOWNLOADING_CHUNKS);
                task.updateTimestamp();
                task.setDownloadChunksStartTime(Util.currentTime());
                this.taskInflightMap.put(task.getTransferId(), task);

                downloadChunks(task, localFile, taskKey, traceId);

                task.setDownloadChunksEndTime(Util.currentTime());
                task.setStatus(DownloadTaskStatus.DOWNLOAD_CHUNKS_COMPLETED);
                task.updateTimestamp();
                this.taskInflightMap.put(task.getTransferId(), task);
            } catch (IOException e) {
                logger.error("[traceId={}] Chunk download failed", traceId, e);
                throw e;
            }

            try {
                task.setStatus(DownloadTaskStatus.MERGING);
                task.updateTimestamp();
                task.setMergeChunksStartTime(Util.currentTime());
                this.taskInflightMap.put(task.getTransferId(), task);

                mergeChunks(task);

                task.setMergeChunksEndTime(Util.currentTime());
                task.updateTimestamp();
                this.taskInflightMap.put(task.getTransferId(), task);
            } catch (IOException e) {
                logger.error("[traceId={}] Chunk merge failed", traceId, e);
                throw e;
            }

            try {
                task.setStatus(DownloadTaskStatus.VERIFYING);
                task.updateTimestamp();
                task.setVerifyStartTime(Util.currentTime());
                this.taskInflightMap.put(task.getTransferId(), task);

                verifyDownload(task, traceId);

                task.setVerifyEndTime(Util.currentTime());
                task.setDownloadSuccessTime(Util.currentTime());
                task.setStatus(DownloadTaskStatus.DOWNLOAD_SUCCESS);
                task.updateTimestamp();
                this.taskInflightMap.put(task.getTransferId(), task);
                handleListenerSuccess(taskKey, task);
            } catch (IOException e) {
                logger.error("[traceId={}] Verify download failed", traceId, e);
                throw e;
            }

            logger.info("[traceId={}] Download task completed: {}", traceId, taskKey);
        } catch (Exception e) {
            logger.error("[traceId={}] Download task failed: {} - {}", traceId, taskKey, e.getMessage(), e);
            task.setStatus(DownloadTaskStatus.FAILED);
            task.updateTimestamp();
            handleListenerError(taskKey, e.getMessage());
        } finally {
            listenerCache.remove(taskKey);
        }
    }

    private void mergeChunks(DownloadTask task) throws IOException {
        File targetFile = new File(task.getLocalFilePath());
        File tmpDir = new File(task.getTmpLocalFilePath());
        String traceId = task.getTraceId();
        
        logger.debug("[traceId={}] Starting merge process: tmpDir={}, targetFile={}", 
                traceId, tmpDir.getAbsolutePath(), targetFile.getAbsolutePath());
        
        if (!tmpDir.exists() || !tmpDir.isDirectory()) {
            throw new IOException("Temporary directory does not exist: " + tmpDir.getAbsolutePath());
        }
        
        File targetParentDir = targetFile.getParentFile();
        if (targetParentDir != null && !targetParentDir.exists()) {
            targetParentDir.mkdirs();
            logger.debug("[traceId={}] Created target parent directory: {}", traceId, targetParentDir.getAbsolutePath());
        }
        
        File tempMergeFile = new File(targetParentDir, "." + targetFile.getName() + ".merge.tmp");
        
        try {
            List<Integer> missingChunks = scanDownloadedChunks(tmpDir, targetFile.getName(), 
                    task.getChunkSize(), task.getTotalSize(), task.getTotalChunks(), traceId);
            
            if (!missingChunks.isEmpty()) {
                throw new IOException("Cannot merge: missing chunks " + missingChunks);
            }
            
            logger.debug("[traceId={}] All chunks verified, merging to temp file: {}", 
                    traceId, tempMergeFile.getAbsolutePath());
            
            try (FileChannel outChannel = FileChannel.open(tempMergeFile.toPath(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                
                for (int i = 0; i < task.getTotalChunks(); i++) {
                    File chunkFile = new File(tmpDir, targetFile.getName() + "_chunk_" + i);
                    
                    if (!chunkFile.exists()) {
                        throw new IOException("Chunk file not found: " + chunkFile.getAbsolutePath());
                    }
                    
                    logger.debug("[traceId={}] Merging chunk {}: {}", traceId, i, chunkFile.getAbsolutePath());
                    
                    try (FileChannel chunkChannel = FileChannel.open(chunkFile.toPath(), StandardOpenOption.READ)) {
                        long chunkSize = chunkChannel.size();
                        long transferred = 0;
                        long position = (long) i * task.getChunkSize();
                        
                        outChannel.position(position);
                        
                        while (transferred < chunkSize) {
                            long count = chunkChannel.transferTo(transferred, chunkSize - transferred, outChannel);
                            if (count <= 0) {
                                throw new IOException("Failed to transfer chunk data at position " + transferred);
                            }
                            transferred += count;
                        }
                        
                        logger.debug("[traceId={}] Transferred {} bytes from chunk {} to position {}", 
                                traceId, transferred, i, position);
                    }
                }
            }
            
            long mergedSize = Files.size(tempMergeFile.toPath());
            logger.debug("[traceId={}] Temp merge file size: {}, expected: {}", 
                    traceId, mergedSize, task.getTotalSize());
            
            if (mergedSize != task.getTotalSize()) {
                logger.error("[traceId={}] Merged file size mismatch. Expected: {}, Actual: {}", 
                        traceId, task.getTotalSize(), mergedSize);
                Files.deleteIfExists(tempMergeFile.toPath());
                throw new IOException(String.format(
                        "Merged file size mismatch. Expected: %d, Actual: %d", 
                        task.getTotalSize(), mergedSize));
            }
            
            logger.debug("[traceId={}] Moving merged file to target: {}", 
                    traceId, targetFile.getAbsolutePath());
            
            Files.move(tempMergeFile.toPath(), targetFile.toPath(), 
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            
            logger.info("[traceId={}] Successfully merged chunks to target file: {} (size={})", 
                    traceId, targetFile.getAbsolutePath(), targetFile.length());
            
            cleanupTempChunks(tmpDir, targetFile.getName(), traceId);
            
        } catch (IOException e) {
            logger.error("[traceId={}] Failed to merge chunks", traceId, e);
            
            if (tempMergeFile.exists()) {
                try {
                    Files.deleteIfExists(tempMergeFile.toPath());
                    logger.debug("[traceId={}] Cleaned up temp merge file: {}", 
                            traceId, tempMergeFile.getAbsolutePath());
                } catch (IOException cleanupEx) {
                    logger.warn("[traceId={}] Failed to cleanup temp merge file: {}", 
                            traceId, tempMergeFile.getAbsolutePath(), cleanupEx);
                }
            }
            
            throw e;
        }
    }
    
    private void cleanupTempChunks(File tmpDir, String fileName, String traceId) {
        logger.debug("[traceId={}] Cleaning up temporary chunk files in: {}", traceId, tmpDir.getAbsolutePath());
        
        Pattern chunkPattern = Pattern.compile(Pattern.quote(fileName) + "_chunk_\\d+");
        
        try {
            Files.list(tmpDir.toPath())
                    .filter(Files::isRegularFile)
                    .filter(file -> chunkPattern.matcher(file.getFileName().toString()).matches())
                    .forEach(file -> {
                        try {
                            Files.deleteIfExists(file);
                            logger.debug("[traceId={}] Deleted chunk file: {}", traceId, file.getFileName());
                        } catch (IOException e) {
                            logger.warn("[traceId={}] Failed to delete chunk file: {}", 
                                    traceId, file.getFileName(), e);
                        }
                    });
            
            try {
                Files.deleteIfExists(tmpDir.toPath());
                logger.debug("[traceId={}] Deleted temporary directory: {}", traceId, tmpDir.getAbsolutePath());
            } catch (IOException e) {
                logger.warn("[traceId={}] Failed to delete temporary directory: {}", 
                        traceId, tmpDir.getAbsolutePath(), e);
            }
            
        } catch (Exception e) {
            logger.error("[traceId={}] Error during temp chunk cleanup", traceId, e);
        }
    }

    private void downloadChunks(DownloadTask task, File file, String taskKey, String traceId) throws IOException {
        final String tmpLocalFilePath = task.getTmpLocalFilePath();
        File tmpDir = new File(tmpLocalFilePath);
        
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        
        List<Integer> missingChunks = scanDownloadedChunks(tmpDir, file.getName(), task.getChunkSize(), task.getTotalSize(), task.getTotalChunks(), traceId);
        AtomicInteger downloadedCount = new AtomicInteger(task.getTotalChunks() - missingChunks.size());
        DownloadListener listener = listenerCache.get(taskKey);
        task.setDownloadedChunksCount(downloadedCount.get());
        
        logger.debug("[traceId={}] Scanned tmp directory: downloaded={}, missing={}", 
                traceId, downloadedCount.get(), missingChunks.size());
        try  {
            CompletableFuture<?>[] downloadFutures = missingChunks.stream()
                    .map(chunkIndex -> CompletableFuture.runAsync(() -> {
                        try {
                            File chunkFile = downloadChunk(task, chunkIndex, traceId);
                            
                            long chunkSize = chunkFile.length();
                            if (chunkSize == 0) {
                                throw new IOException("Empty chunk file downloaded for chunk " + chunkIndex);
                            }
                            
                            int expectedChunkSize = calculateExpectedChunkSize(chunkIndex, task.getChunkSize(), task.getTotalSize());
                            if (chunkSize != expectedChunkSize) {
                                throw new IOException(String.format(
                                        "Chunk size mismatch for chunk %d: expected=%d, actual=%d",
                                        chunkIndex, expectedChunkSize, chunkSize));
                            }
                            
                            applyRateLimit((int) chunkSize, traceId);

                            int currentDownloaded = downloadedCount.incrementAndGet();
                            task.incrementDownloadChunksCount();
                            task.updateTimestamp();
                            this.taskInflightMap.put(task.getTransferId(), task);

                            if (listener != null) {
                                double progress = (double) currentDownloaded / task.getTotalChunks() * 100.0;
                                handleListenerProgress(taskKey, task.getTotalChunks(), currentDownloaded, progress);
                            }
                        } catch (Exception e) {
                            throw new CompletionException("Failed to download chunk " + chunkIndex, e);
                        }
                    }, downloadExecutor))
                    .toArray(CompletableFuture[]::new);

            CompletableFuture<Void> allDownloads = CompletableFuture.allOf(downloadFutures);

            int chunksToDownload = missingChunks.size();
            long waves = (long) Math.ceil((double) chunksToDownload / this.concurrentDownloads);
            long timeoutSeconds = (waves + 2) * this.requestTimeoutSeconds;
            timeoutSeconds = Math.max(60, timeoutSeconds);

            try {
                allDownloads.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new IOException("Chunk downloads timed out after " + timeoutSeconds + " seconds", e);
            }
        } catch (Exception e) {
            throw new IOException("Failed to download chunks", e);
        }
    }
    
    private List<Integer> scanDownloadedChunks(File tmpDir, String fileName, int chunkSize, long totalSize, int totalChunks, String traceId) {
        List<Integer> downloadedChunks = new ArrayList<>();
        
        if (!tmpDir.exists() || !tmpDir.isDirectory()) {
            logger.debug("[traceId={}] Tmp directory does not exist: {}", traceId, tmpDir.getAbsolutePath());
            for (int i = 0; i < totalChunks; i++) {
                downloadedChunks.add(i);
            }
            return downloadedChunks;
        }
        
        Pattern chunkPattern = Pattern.compile(Pattern.quote(fileName) + "_chunk_(\\d+)");
        
        try {
            Files.list(tmpDir.toPath())
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        String chunkFileName = file.getFileName().toString();
                        Matcher matcher = chunkPattern.matcher(chunkFileName);
                        
                        if (matcher.matches()) {
                            try {
                                int chunkIndex = Integer.parseInt(matcher.group(1));
                                
                                if (chunkIndex >= 0 && chunkIndex < totalChunks) {
                                    long fileSize = Files.size(file);
                                    long expectedSize = calculateExpectedChunkSize(chunkIndex, chunkSize, totalSize);
                                    
                                    if (fileSize == expectedSize) {
                                        downloadedChunks.add(chunkIndex);
                                        logger.debug("[traceId={}] Found valid chunk file: {} (index={}, size={})",
                                                traceId, chunkFileName, chunkIndex, fileSize);
                                    } else {
                                        logger.warn("[traceId={}] Chunk file size mismatch: {} (index={}, expected={}, actual={})",
                                                traceId, chunkFileName, chunkIndex, expectedSize, fileSize);
                                        Files.deleteIfExists(file);
                                    }
                                } else {
                                    logger.warn("[traceId={}] Invalid chunk index in file name: {} (index={}, totalChunks={})",
                                            traceId, chunkFileName, chunkIndex, totalChunks);
                                }
                            } catch (Exception e) {
                                logger.error("[traceId={}] Error processing chunk file {}: {}", traceId, chunkFileName, e.getMessage());
                            }
                        }
                    });
        } catch (Exception e) {
            logger.error("Error scanning tmp directory: {}", tmpDir.getAbsolutePath(), e);
        }
        
        List<Integer> missingChunks = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            if (!downloadedChunks.contains(i)) {
                missingChunks.add(i);
            }
        }
        
        return missingChunks;
    }
    
    private int calculateExpectedChunkSize(int chunkIndex, int chunkSize, long totalSize) {
        if (chunkIndex == (totalSize + chunkSize -1) / chunkSize -1) {
            return (int) (totalSize - (long) chunkIndex * chunkSize);
        }
        return chunkSize;
    }

    private void applyRateLimit(int dataSize, String traceId) throws InterruptedException {
        if (rateLimiter == null || dataSize <= 0) {
            return;
        }
        rateLimiter.acquire(dataSize, traceId);
    }

    private void handleListenerProgress(String taskKey, int total, int downloaded, double progress) {
        DownloadListener listener = listenerCache.get(taskKey);
        if (listener == null) return;
        try {
            listener.onProgress(total, downloaded, progress);
        } catch (Exception e) {
            logger.warn("DownloadListener.onProgress failed: {}", e.getMessage());
        }
    }

    private void handleListenerSuccess(String taskKey, DownloadTask result) {
        DownloadListener listener = listenerCache.get(taskKey);
        if (listener == null) return;
        try {
            listener.onComplete(result);
        } catch (Exception e) {
            logger.warn("DownloadListener.onComplete failed: {}", e.getMessage());
        }
    }

    private void handleListenerError(String taskKey, String message) {
        DownloadListener listener = taskKey != null ? listenerCache.get(taskKey) : null;
        handleListenerError(listener, message);
    }

    private void handleListenerError(DownloadListener listener, String message) {
        if (listener == null) return;
        try {
            listener.onError(message);
        } catch (Exception e) {
            logger.warn("DownloadListener.onError failed: {}", e.getMessage());
        }
    }

    private DownloadListener createListenerInstance(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (instance instanceof DownloadListener) {
                return (DownloadListener) instance;
            } else {
                logger.error("Class {} does not implement DownloadListener interface", className);
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

    private static final Type API_RESPONSE_DOWNLOAD_INIT = TypeToken.getParameterized(ApiResponse.class, ChunkDownloadInfoResponse.class).getType();
    private static final Type API_RESPONSE_DOWNLOAD_CHUNK = TypeToken.getParameterized(ApiResponse.class, ChunkDownloadResponse.class).getType();
    private static final Type API_RESPONSE_LONG = TypeToken.getParameterized(ApiResponse.class, Long.class).getType();

    private long getRemoteFileSize(String remoteFilePath, String traceId) throws IOException, InterruptedException {
        String encodedPath = java.net.URLEncoder.encode(remoteFilePath, StandardCharsets.UTF_8);
        ApiResponse<Long> response = getApi("api/file/size?path=" + encodedPath, API_RESPONSE_LONG, traceId);
        if (!response.isSuccess()) {
            logger.error("[traceId={}] Failed to get remote file size: {}", traceId, response.getMsg());
            return -1;
        }
        return response.getData() != null ? response.getData() : -1;
    }

    private ApiResponse<ChunkDownloadInfoResponse> getDownloadInfo(DownloadTask task) throws IOException, InterruptedException {
        logger.debug("[traceId={}] Initializing download: remote={}, local={}, size={}",
                task.getTraceId(), task.getRemoteFilePath(), task.getLocalFilePath(), task.getTotalSize());

        ChunkDownloadInfoRequest req = new ChunkDownloadInfoRequest();
        req.setTransferId(task.getTransferId());
        req.setRemoteFilePath(task.getRemoteFilePath());
        req.setTraceId(task.getTraceId());

        if (agentConfig != null) {
            req.setSourceAgentId(agentConfig.getAgentId());
            req.setSourceAgentIp(agentConfig.getAgentIp());
            req.setSourceAgentPort(agentConfig.getServerPort());

//            File destFile = Paths.get(task.getLocalFilePath()).toFile();
//            req.setDestFileDir(Util.transferToLinuxPath(destFile.getParent()));
//            req.setDestFileName(destFile.getName());
        }

        try {
            URI uri = new URI(agentApiUrl);
            req.setDestAgentIp(uri.getHost());
            req.setDestAgentPort(uri.getPort());
        } catch (java.net.URISyntaxException e) {
            logger.warn("Could not parse agentApiUrl to extract host and port", e);
        }

        return postApi("api/file/chunk/download/info", req, API_RESPONSE_DOWNLOAD_INIT, task.getTraceId());
    }

    private File downloadChunk(DownloadTask task, int chunkIndex, String traceId) throws IOException, InterruptedException {
        logger.debug("[traceId={}] Downloading chunk {} for transferId: {} using zero-copy", traceId, chunkIndex, task.getTransferId());

        File destFile = Paths.get(task.getRemoteFilePath()).toFile();
        String destFileDir = Util.transferToLinuxPath(destFile.getParent());
        String destFileName = destFile.getName();

        ChunkDownloadRequest req = new ChunkDownloadRequest();
        req.setTransferId(task.getTransferId());
        req.setChunkIndex(chunkIndex);
        req.setDestFileDir(destFileDir);
        req.setDestFileName(destFileName);
        req.setTraceId(traceId);

        File tmpDir = new File(task.getTmpLocalFilePath());
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }

        File chunkFile = new File(tmpDir, destFileName + "_chunk_" + chunkIndex);

        try {
            String url = agentApiUrl + "api/file/chunk/download";
            String jsonBody = gson.toJson(req);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("X-Trace-Id", traceId)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(chunkFile.toPath()));

            if (response.statusCode() != 200) {
                throw new IOException("Chunk download failed with status code: " + response.statusCode());
            }

            long downloadedSize = Files.size(chunkFile.toPath());
            if (downloadedSize == 0) {
                throw new IOException("Empty chunk file downloaded");
            }

            int expectedChunkSize = calculateExpectedChunkSize(chunkIndex, task.getChunkSize(), task.getTotalSize());
            if (downloadedSize != expectedChunkSize) {
                throw new IOException(String.format(
                        "Chunk size mismatch for chunk %d: expected=%d, actual=%d",
                        chunkIndex, expectedChunkSize, downloadedSize));
            }

            logger.debug("[traceId={}] Chunk downloaded successfully using zero-copy: transferId={}, chunkIndex={}, size={}, file={}",
                    traceId, task.getTransferId(), chunkIndex, downloadedSize, chunkFile.getAbsolutePath());

            return chunkFile;
        } catch (Exception e) {
            logger.error("[traceId={}] Failed to download chunk", traceId, e);
            if (chunkFile.exists()) {
                try {
                    Files.deleteIfExists(chunkFile.toPath());
                } catch (IOException cleanupEx) {
                    logger.warn("[traceId={}] Failed to cleanup chunk file: {}", traceId, chunkFile.getAbsolutePath(), cleanupEx);
                }
            }
            throw new IOException("Failed to download chunk: " + e.getMessage(), e);
        }
    }

    private void verifyDownload(DownloadTask task, String traceId) throws IOException {
        File localFile = new File(task.getLocalFilePath());
        if (!localFile.exists()) {
            throw new IOException("Downloaded file does not exist: " + task.getLocalFilePath());
        }

        long actualSize = localFile.length();
        if (actualSize != task.getTotalSize()) {
            throw new IOException("Downloaded file size mismatch. Expected: " + task.getTotalSize() + ", Actual: " + actualSize);
        }

        logger.debug("[traceId={}] Download verification passed: file={}, size={}", traceId, task.getLocalFilePath(), actualSize);
    }

    private <T> ApiResponse<T> getApi(String endpoint, Type responseType, String traceId) throws IOException, InterruptedException {
        try {
            String url = agentApiUrl + endpoint;
            if (!url.contains("traceId")) {
                url += (url.contains("?") ? "&" : "?") + "traceId=" + traceId;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
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

    private <T> ApiResponse<T> postApi(String endpoint, Object requestBody, Type responseType, String traceId) throws IOException, InterruptedException {
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

    public void shutdown() {
        logger.info("Shutting down AgentDownloader...");
        shutdown = true;
        workerExecutor.shutdown();
        downloadExecutor.shutdown();
        try {
            if (!workerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                workerExecutor.shutdownNow();
            }
            if (!downloadExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                downloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workerExecutor.shutdownNow();
            downloadExecutor.shutdownNow();
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
        logger.info("AgentDownloader shut down");
    }
}