package com.cq.agent.client.download;

import com.cq.agent.client.BaseAgentClient;
import com.cq.agent.client.RemoteAgentInfo;
import com.cq.agent.client.upload.Util;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgentDownloader extends BaseAgentClient<DownloadTask, DownloadListener> {

    private static final Logger logger = LoggerFactory.getLogger(AgentDownloader.class);

    public AgentDownloader(AgentConfig agentConfig) {
        super(agentConfig, agentConfig.getAgentApiUrl(), agentConfig.getDownloadConcurrentDownloads(),
                agentConfig.getDownloadMaxQueueDepth(), agentConfig.getDownloadWorkerCount(),
                agentConfig.getDownloadMaxRetries(), agentConfig.getDownloadRetryDelayMs(),
                agentConfig.getDownloadConnectTimeoutSeconds(), agentConfig.getDownloadRequestTimeoutSeconds(),
                agentConfig.getDownloadQueueDbPath(), agentConfig.getDownloadMapDbPath(), agentConfig.getMaxDownloadRateKBPerSecond(), DownloadTask.class, "download");
    }

    @Override
    protected void processTask(DownloadTask task) {
        String taskKey = getTaskKey(task);
        String traceId = task.getTraceId();

        try {
            task.setStatus(DownloadTaskStatus.SCANNED);
            task.updateTimestamp();
            task.incrementRetryCount();
            this.taskInflightMap.put(task.getTransferId(), task);

            String listenerClassName = task.getListenerClassName();
            if (listenerClassName != null && !listenerCache.containsKey(taskKey)) {
                DownloadListener listener = createListenerInstance(listenerClassName, DownloadListener.class);
                if (listener != null) {
                    listenerCache.put(taskKey, listener);
                }
            }

            File localFile = new File(task.getLocalFilePath());
            File parentDir = localFile.getParentFile();
            if (parentDir == null) {
                logger.error("[traceId={}] Failed to get parent directory: {}", traceId, parentDir.getAbsolutePath());
                throw new IOException("Failed to get parent directory: " + parentDir.getAbsolutePath());
            }
            if (!parentDir.exists()) {
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
            task.setInitDownloadStartTime(Util.currentTime());
            task.setStatus(DownloadTaskStatus.INIT_DOWNLOAD_COMPLETED);
            task.updateTimestamp();
            this.taskInflightMap.put(task.getTransferId(), task);

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
            task.setExceptionDesc(e.getMessage());
            this.taskInflightMap.put(task.getTransferId(), task);
            handleListenerError(taskKey, e.getMessage());
        } finally {
            listenerCache.remove(taskKey);
        }
    }

    @Override
    protected String getTaskKey(DownloadTask task) {
        return Util.md5(task.getRemoteFilePath() + ":" + task.getLocalFilePath());
    }

    @Override
    protected void onListenerProgress(DownloadListener listener, int total, int processed, double progress) {
        listener.onProgress(total, processed, progress);
    }

    @Override
    protected void onListenerBeforeSend(DownloadListener listener, DownloadTask task) {
        listener.onBeforeSend(task);
    }

    @Override
    protected void onListenerSuccess(DownloadListener listener, DownloadTask result) {
        listener.onComplete(result);
    }

    @Override
    protected void onListenerError(DownloadListener listener, String message) {
        listener.onError(message);
    }

    public boolean downloadFile(String remoteFileInfo, String localFilePath, DownloadListener listener) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        try {
            if (taskQueue.size() > maxQueueDepth) {
                handleListenerError(listener, "Download queue depth is reached: " + maxQueueDepth);
                return false;
            }
            // remoteTargetPath like 192.168.1.100:7777@root:/tmp/upload
            // ip:port@username:destFilePath
            if (remoteFileInfo == null || remoteFileInfo.isBlank()) {
                handleListenerError(listener, "remoteFilePath must not be null or blank");
                return false;
            }

            RemoteAgentInfo remoteAgentInfo;
            try {
                remoteAgentInfo = Util.resolveRemoteAgentInfo(remoteFileInfo);
            } catch (Exception e) {
                handleListenerError(listener, e.getMessage());
                return false;
            }

            if (localFilePath == null || localFilePath.isBlank()) {
                handleListenerError(listener, "localFilePath must not be null or blank");
                return false;
            }

            File localFile = new File(localFilePath);
            File parentDir = localFile.getParentFile();
            if (parentDir == null) {
                handleListenerError(listener, "localFilePath must have a parent directory");
                return false;
            }
            if (!parentDir.exists()) {
                final boolean res = parentDir.mkdirs();
                if (!res) {
                    handleListenerError(listener, "Failed to create directory: " + parentDir.getAbsolutePath());
                    return false;
                }
            }
            if (!parentDir.canWrite()) {
                handleListenerError(listener, "parent directory: " + parentDir.getAbsolutePath() + " must hava write permission!");
                return false;
            }

            String remoteAgentApiUrl = "http://" + remoteAgentInfo.getIp() + ":" + remoteAgentInfo.getPort() + "/";

            long fileSize = getRemoteFileSize(remoteAgentApiUrl, remoteAgentInfo.getDestFilePath(), traceId);
            if (fileSize <= 0) {
                handleListenerError(listener, "Failed to get remote file size");
                return false;
            }
            DownloadTask task = new DownloadTask(remoteAgentInfo.getDestFilePath(), localFilePath, fileSize,remoteAgentApiUrl , remoteAgentInfo.getUsername());
            task.setTransferId(UUID.randomUUID().toString().replace("-", ""));
            task.setTraceId(traceId);
            task.setEnqueuedTime(Util.currentTime());
            task.setListenerClassName(listener != null ? listener.getClass().getName() : null);
            task.updateTimestamp();

            String taskKey = getTaskKey(task);

            if (listener != null) {
                listenerCache.put(taskKey, listener);
            }

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

    private static final Type API_RESPONSE_DOWNLOAD_INIT = com.google.gson.reflect.TypeToken.getParameterized(ApiResponse.class, ChunkDownloadInfoResponse.class).getType();
    private static final Type API_RESPONSE_DOWNLOAD_CHUNK = com.google.gson.reflect.TypeToken.getParameterized(ApiResponse.class, ChunkDownloadResponse.class).getType();
    private static final Type API_RESPONSE_LONG = com.google.gson.reflect.TypeToken.getParameterized(ApiResponse.class, Long.class).getType();

    private long getRemoteFileSize(String remoteAgentApiUrl, String remoteFilePath, String traceId) throws IOException, InterruptedException {
        String encodedPath = java.net.URLEncoder.encode(remoteFilePath, StandardCharsets.UTF_8);
        ApiResponse<Long> response = getApi(remoteAgentApiUrl, "api/file/size?path=" + encodedPath, API_RESPONSE_LONG, traceId);
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
        }

        try {
            URI uri = new URI(agentApiUrl);
            req.setDestAgentIp(uri.getHost());
            req.setDestAgentPort(uri.getPort());
        } catch (java.net.URISyntaxException e) {
            logger.warn("Could not parse agentApiUrl to extract host and port", e);
        }

        return postApi(task.getRemoteAgentApiUrl(), "api/file/chunk/download/info", req, API_RESPONSE_DOWNLOAD_INIT, task.getTraceId());
    }

    private File downloadChunk(DownloadTask task, int chunkIndex, String traceId) throws IOException, InterruptedException {
        logger.debug("[traceId={}] Downloading chunk {} for transferId: {} using zero-copy", traceId, chunkIndex, task.getTransferId());

        File destFile = Paths.get(task.getRemoteFilePath()).toFile();
        String destFileDir = com.cq.agent.client.upload.Util.transferToLinuxPath(destFile.getParent());
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

    private int calculateExpectedChunkSize(int chunkIndex, int chunkSize, long totalSize) {
        if (chunkIndex == (totalSize + chunkSize -1) / chunkSize -1) {
            return (int) (totalSize - (long) chunkIndex * chunkSize);
        }
        return chunkSize;
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
                            if (listener != null) {
                                handleListenerBeforeSend(taskKey, task);
                            }
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
                    }, chunkExecutor))
                    .toArray(CompletableFuture[]::new);

            CompletableFuture<Void> allDownloads = CompletableFuture.allOf(downloadFutures);

            int chunksToDownload = missingChunks.size();
            long waves = (long) Math.ceil((double) chunksToDownload / this.concurrentThreads);
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
                                    int expectedSize = calculateExpectedChunkSize(chunkIndex, chunkSize, totalSize);
                                    if (fileSize == expectedSize) {
                                        downloadedChunks.add(chunkIndex);
                                    } else {
                                        logger.warn("[traceId={}] Chunk file size mismatch: {} (expected={}, actual={})", 
                                                traceId, chunkFileName, expectedSize, fileSize);
                                    }
                                }
                            } catch (NumberFormatException e) {
                                logger.warn("[traceId={}] Invalid chunk file name: {}", traceId, chunkFileName);
                            } catch (IOException e) {
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
}