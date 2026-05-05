package com.cq.agent.client.upload;

import com.cq.agent.batch.postprocess.PostTransferConfig;
import com.cq.agent.batch.postprocess.PostTransferHandler;
import com.cq.agent.batch.postprocess.PostProcessResult;
import com.cq.agent.batch.queue.BatchTransferQueueManager;
import com.cq.agent.batch.queue.BatchUploadTask;
import com.cq.agent.batch.queue.QueueMetrics;
import com.cq.agent.batch.report.ProgressReport;
import com.cq.agent.batch.report.ProxyReportClient;
import com.cq.agent.batch.report.QueueSnapshotReport;
import com.cq.agent.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BatchAwareAgentUploader extends AgentUploader {
    private static final Logger logger = LoggerFactory.getLogger(BatchAwareAgentUploader.class);
    private static final long PROGRESS_REPORT_INTERVAL_MS = 5000L;

    private final AgentConfig agentConfig;
    private final BatchTransferQueueManager queueManager;
    private final ProxyReportClient proxyReportClient;
    private final ExecutorService batchWorkerPool;
    private final ScheduledExecutorService snapshotReporter;
    private final Map<Long, AtomicInteger> activeTaskCounters = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private volatile Long appliedBandwidthLimitBytesPerSec;

    public BatchAwareAgentUploader(AgentConfig agentConfig,
            BatchTransferQueueManager queueManager,
            ProxyReportClient proxyReportClient) {
        super(agentConfig);
        this.agentConfig = agentConfig;
        this.queueManager = queueManager;
        this.proxyReportClient = proxyReportClient;
        this.batchWorkerPool = Executors.newFixedThreadPool(Math.max(1, agentConfig.getUploadWorkerCount()), r -> {
            Thread thread = new Thread(r, "batch-upload-worker");
            thread.setDaemon(true);
            return thread;
        });
        this.snapshotReporter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "batch-queue-reporter");
            thread.setDaemon(true);
            return thread;
        });
        startWorkers();
        startSnapshotReporter();
    }

    public void applyBandwidthLimit(Long maxBandwidthBytesPerSec) {
        this.appliedBandwidthLimitBytesPerSec = maxBandwidthBytesPerSec == null || maxBandwidthBytesPerSec <= 0
                ? null
                : maxBandwidthBytesPerSec;
    }

    public boolean enqueue(BatchUploadTask task) {
        if (task == null) {
            return false;
        }
        boolean accepted = queueManager.enqueue(task);
        if (accepted) {
            markTaskActive(task.getTaskId());
            reportQueued(task);
        }
        return accepted;
    }

    public void shutdownBatch() {
        running = false;
        snapshotReporter.shutdownNow();
        batchWorkerPool.shutdownNow();
        queueManager.close();
        super.shutdown();
    }

    private void startWorkers() {
        for (int i = 0; i < Math.max(1, agentConfig.getUploadWorkerCount()); i++) {
            batchWorkerPool.submit(this::runBatchWorker);
        }
    }

    private void runBatchWorker() {
        while (running) {
            try {
                BatchUploadTask task = queueManager.dequeue(1000L);
                if (task == null) {
                    continue;
                }
                executeBatchTask(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                logger.error("Batch upload worker failed: {}", e.getMessage(), e);
            }
        }
    }

    private void executeBatchTask(BatchUploadTask batchTask) {
        UploadTask uploadTask = toUploadTask(batchTask);
        String taskKey = getTaskKey(uploadTask);
        BatchUploadListener listener = new BatchUploadListener(batchTask, uploadTask);
        listenerCache.put(taskKey, listener);
        try {
            processTask(uploadTask);
        } finally {
            listenerCache.remove(taskKey);
        }

        if (uploadTask.getStatus() == UploadTaskStatus.UPLOAD_SUCCESS) {
            queueManager.recordCompletion();
            completeTaskActivity(batchTask.getTaskId());
            reportCompleted(batchTask, uploadTask);
            executePostProcess(batchTask);
            return;
        }

        ErrorClassifier.Classification classification = ErrorClassifier
                .classify(new RuntimeException(uploadTask.getExceptionDesc()));
        if (classification.isRetryable()) {
            Date nextRetryAt = queueManager.enqueueForRetryAndGetScheduleTime(batchTask, 0L);
            if (nextRetryAt != null) {
                reportRetrying(batchTask, uploadTask, classification, nextRetryAt);
                return;
            }
        }

        if (!classification.isRetryable()) {
            queueManager.recordFailure();
        }
        completeTaskActivity(batchTask.getTaskId());
        reportFailed(batchTask, uploadTask, classification);
    }

    private UploadTask toUploadTask(BatchUploadTask batchTask) {
        String localFilePath = resolveLocalFilePath(batchTask);
        String remoteTargetPath = resolveRemoteTargetPath(batchTask);
        String remoteAgentApiUrl = normalizeAgentApiUrl(batchTask.getTargetAgentApiUrl(), batchTask.getTargetAgentId());
        UploadTask uploadTask = new UploadTask(localFilePath, remoteTargetPath, batchTask.getFileSizeBytes(),
                remoteAgentApiUrl, "batch");
        uploadTask.setTransferId(
                "batch-" + batchTask.getSubtaskId() + "-" + UUID.randomUUID().toString().replace("-", ""));
        uploadTask.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        uploadTask.setEnqueuedTime(Util.currentTime());
        uploadTask.updateTimestamp();
        return uploadTask;
    }

    private String resolveLocalFilePath(BatchUploadTask batchTask) {
        if (batchTask.getAbsolutePath() != null && !batchTask.getAbsolutePath().isBlank()) {
            return Path.of(batchTask.getAbsolutePath()).toAbsolutePath().normalize().toString();
        }
        return Path.of(agentConfig.getFileBaseDirectory())
                .resolve(normalizeRelativePath(batchTask.getFilePath()))
                .toAbsolutePath()
                .normalize()
                .toString();
    }

    private String resolveRemoteTargetPath(BatchUploadTask batchTask) {
        String targetDir = batchTask.getTargetDir();
        String filePath = batchTask.getFilePath();
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath不能为空");
        }
        String fileName = filePath.contains("/") ? filePath.substring(filePath.lastIndexOf('/') + 1) : filePath;
        if (targetDir != null && !targetDir.isBlank()) {
            return Path.of(targetDir).resolve(fileName).normalize().toString();
        }
        return normalizeRelativePath(filePath);
    }

    private String normalizeAgentApiUrl(String targetAgentApiUrl, String targetAgentId) {
        String raw = targetAgentApiUrl;
        if ((raw == null || raw.isBlank()) && targetAgentId != null && targetAgentId.startsWith("http")) {
            raw = targetAgentId;
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("targetAgentApiUrl不能为空");
        }
        return raw.endsWith("/") ? raw : raw + "/";
    }

    private String normalizeRelativePath(String filePath) {
        return filePath == null ? "" : filePath.replace('\\', '/');
    }

    private void reportQueued(BatchUploadTask task) {
        ProgressReport report = baseReport(task, "QUEUED", 0, 0, 0L, task.getFileSizeBytes(), 0L, 0L);
        proxyReportClient.asyncReportProgress(report);
    }

    private void reportRetrying(BatchUploadTask task,
            UploadTask uploadTask,
            ErrorClassifier.Classification classification,
            Date nextRetryAt) {
        ProgressReport report = baseReport(task, "RETRYING",
                uploadTask.getUploadChunksCount().get(),
                uploadTask.getTotalChunks(),
                Math.min(task.getFileSizeBytes(),
                        (long) uploadTask.getUploadChunksCount().get() * Math.max(1, uploadTask.getChunkSize())),
                task.getFileSizeBytes(),
                0L,
                0L);
        report.setTimestamp(nextRetryAt);
        proxyReportClient.asyncReportProgress(report);
        logger.warn("Subtask enters retry queue, subtaskId={}, taskId={}, reason={}, nextRetryAt={}",
                task.getSubtaskId(), task.getTaskId(), classification.getDescription(), nextRetryAt);
    }

    private void reportFailed(BatchUploadTask task,
            UploadTask uploadTask,
            ErrorClassifier.Classification classification) {
        ProgressReport report = baseReport(task, "FAILED",
                uploadTask.getUploadChunksCount().get(),
                uploadTask.getTotalChunks(),
                Math.min(task.getFileSizeBytes(),
                        (long) uploadTask.getUploadChunksCount().get() * Math.max(1, uploadTask.getChunkSize())),
                task.getFileSizeBytes(),
                0L,
                0L);
        proxyReportClient.asyncReportProgress(report);
        logger.error("Batch upload failed, subtaskId={}, taskId={}, type={}, message={}",
                task.getSubtaskId(), task.getTaskId(), classification.getType(), uploadTask.getExceptionDesc());
    }

    private void reportCompleted(BatchUploadTask task, UploadTask uploadTask) {
        ProgressReport report = baseReport(task, "COMPLETED",
                uploadTask.getTotalChunks(), uploadTask.getTotalChunks(), task.getFileSizeBytes(),
                task.getFileSizeBytes(),
                0L, 0L);
        proxyReportClient.asyncReportProgress(report);
        logger.info("Batch upload completed, subtaskId={}, taskId={}, filePath={}",
                task.getSubtaskId(), task.getTaskId(), task.getFilePath());
    }

    private void executePostProcess(BatchUploadTask batchTask) {
        String postAction = batchTask.getPostAction();
        if (postAction == null || postAction.isBlank() || "NONE".equalsIgnoreCase(postAction)) {
            return;
        }
        try {
            PostTransferConfig config = new PostTransferConfig();
            config.setAction(postAction.toUpperCase());
            config.setSourceBaseDir(batchTask.getSourceBaseDir());
            config.setBackupDir(batchTask.getBackupDir());
            config.setBackupMode(batchTask.getBackupMode() != null ? batchTask.getBackupMode() : "COPY");
            config.setPreserveDirStructure(batchTask.isPreserveDirStructure());

            PostTransferHandler handler = new PostTransferHandler();
            PostProcessResult result = handler.execute(
                    batchTask.getTaskId(),
                    java.util.List.of(batchTask.getFilePath()),
                    config);

            logger.info("Post process done, subtaskId={}, taskId={}, action={}, success={}, failed={}",
                    batchTask.getSubtaskId(), batchTask.getTaskId(), postAction,
                    result.getSuccessCount(), result.getFailedCount());

            reportPostProcessResult(batchTask, result);
        } catch (Exception e) {
            logger.error("Post process failed, subtaskId={}, taskId={}, action={}, error={}",
                    batchTask.getSubtaskId(), batchTask.getTaskId(), postAction, e.getMessage());
        }
    }

    private void reportPostProcessResult(BatchUploadTask task, PostProcessResult result) {
        try {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("taskId", task.getTaskId());
            body.put("successFiles", result.getSuccessFiles());
            body.put("action", result.getAction() != null ? result.getAction() : task.getPostAction());
            body.put("sourceBaseDir", task.getSourceBaseDir());
            body.put("backupDir", task.getBackupDir());
            body.put("backupMode", task.getBackupMode());
            body.put("successCount", result.getSuccessCount());
            body.put("failedCount", result.getFailedCount());
            body.put("durationMs", result.getDurationMs());
            proxyReportClient.reportPostProcessResult(task.getTaskId(), body);
        } catch (Exception e) {
            logger.warn("Failed to report post-process result for subtaskId={}: {}", task.getSubtaskId(), e.getMessage());
        }
    }

    private ProgressReport baseReport(BatchUploadTask batchTask,
            String status,
            int transferredChunks,
            int totalChunks,
            long transferredBytes,
            long totalBytes,
            long currentSpeed,
            long avgSpeed) {
        ProgressReport report = new ProgressReport();
        report.setReportId("rpt-" + UUID.randomUUID());
        report.setTaskId(batchTask.getTaskId());
        report.setSubtaskId(batchTask.getSubtaskId());
        report.setTargetAgentId(batchTask.getTargetAgentId());
        report.setFilePath(batchTask.getFilePath());
        report.setStatus(status);
        ProgressReport.ProgressDetail progressDetail = new ProgressReport.ProgressDetail();
        progressDetail.setTransferredChunks(transferredChunks);
        progressDetail.setTotalChunks(totalChunks);
        progressDetail.setTransferredBytes(transferredBytes);
        progressDetail.setTotalBytes(totalBytes);
        report.setProgress(progressDetail);
        ProgressReport.PerformanceInfo performanceInfo = new ProgressReport.PerformanceInfo();
        performanceInfo.setCurrentSpeedBytesPerSec(currentSpeed);
        performanceInfo.setAvgSpeedBytesPerSec(avgSpeed);
        report.setPerformance(performanceInfo);
        report.setTimestamp(new Date());
        return report;
    }

    private void startSnapshotReporter() {
        snapshotReporter.scheduleAtFixedRate(() -> {
            try {
                reportQueueSnapshot();
            } catch (Exception e) {
                logger.warn("Failed to report queue snapshot: {}", e.getMessage());
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private void reportQueueSnapshot() {
        QueueMetrics metrics = queueManager.getMetrics();
        QueueSnapshotReport snapshot = new QueueSnapshotReport();
        snapshot.setAgentId(agentConfig.getAgentId());
        snapshot.setSnapshotTime(new Date());

        QueueSnapshotReport.QueueInfo sendQueue = new QueueSnapshotReport.QueueInfo();
        sendQueue.setDepth(metrics.getSendQueueDepth());
        sendQueue.setPeakDepth(metrics.getSendQueuePeakDepth());
        sendQueue.setCapacity(metrics.getSendQueueCapacity());
        sendQueue.setAvgWaitTimeMs(metrics.getSendQueueAvgWaitMs());
        snapshot.setSendQueue(sendQueue);

        QueueSnapshotReport.QueueInfo retryQueue = new QueueSnapshotReport.QueueInfo();
        retryQueue.setDepth(metrics.getRetryQueueDepth());
        retryQueue.setPeakDepth(metrics.getRetryQueuePeakDepth());
        retryQueue.setCapacity(metrics.getRetryQueueCapacity());
        retryQueue.setNextScheduleTime(metrics.getRetryNextScheduleTime());
        snapshot.setRetryQueue(retryQueue);

        QueueSnapshotReport.ProcessingStats processingStats = new QueueSnapshotReport.ProcessingStats();
        processingStats.setCompletedLast1Min((int) Math.round(metrics.getProcessingRatePerSec() * 60));
        processingStats.setFailedLast1Min(0);
        processingStats.setAvgProcessTimeMs(metrics.getSendQueueAvgWaitMs());
        snapshot.setProcessingStats(processingStats);
        snapshot.setActiveTasks(activeTaskCounters.entrySet().stream()
                .filter(entry -> entry.getValue().get() > 0)
                .map(Map.Entry::getKey)
                .sorted()
                .toList());
        proxyReportClient.reportQueueSnapshot(snapshot);
    }

    private void markTaskActive(Long taskId) {
        if (taskId == null) {
            return;
        }
        activeTaskCounters.computeIfAbsent(taskId, key -> new AtomicInteger()).incrementAndGet();
    }

    private void completeTaskActivity(Long taskId) {
        if (taskId == null) {
            return;
        }
        activeTaskCounters.computeIfPresent(taskId, (key, counter) -> counter.decrementAndGet() <= 0 ? null : counter);
    }

    private final class BatchUploadListener implements UploadListener {
        private final BatchUploadTask batchTask;
        private final UploadTask uploadTask;
        private final long startedAtMs = System.currentTimeMillis();
        private volatile long lastReportAtMs = 0L;
        private volatile long lastTransferredBytes = 0L;
        private volatile boolean sendingStarted = false;

        private BatchUploadListener(BatchUploadTask batchTask, UploadTask uploadTask) {
            this.batchTask = batchTask;
            this.uploadTask = uploadTask;
        }

        @Override
        public synchronized void onBeforeSend(UploadTask task) {
            if (!sendingStarted) {
                sendingStarted = true;
                proxyReportClient.asyncReportProgress(baseReport(batchTask, "SENDING", 0, task.getTotalChunks(), 0L,
                        batchTask.getFileSizeBytes(), 0L, 0L));
            }
        }

        @Override
        public synchronized void onProgress(int totalChunks, int uploadedChunks, double progress) {
            long now = System.currentTimeMillis();
            long transferredBytes = Math.min(batchTask.getFileSizeBytes(),
                    (long) uploadedChunks * Math.max(1, uploadTask.getChunkSize()));
            if (lastReportAtMs > 0 && now - lastReportAtMs < PROGRESS_REPORT_INTERVAL_MS
                    && uploadedChunks < totalChunks) {
                return;
            }
            long currentSpeed = 0L;
            if (lastReportAtMs > 0 && now > lastReportAtMs) {
                currentSpeed = Math.max(0L, (transferredBytes - lastTransferredBytes) * 1000L / (now - lastReportAtMs));
            }
            long avgSpeed = now > startedAtMs ? Math.max(0L, transferredBytes * 1000L / (now - startedAtMs)) : 0L;
            proxyReportClient.asyncReportProgress(baseReport(batchTask, "SENDING", uploadedChunks, totalChunks,
                    transferredBytes, batchTask.getFileSizeBytes(), currentSpeed, avgSpeed));
            lastTransferredBytes = transferredBytes;
            lastReportAtMs = now;
        }

        @Override
        public synchronized void onComplete(UploadTask task) {
            proxyReportClient.asyncReportProgress(baseReport(batchTask, "COMPLETED",
                    task.getTotalChunks(), task.getTotalChunks(), batchTask.getFileSizeBytes(),
                    batchTask.getFileSizeBytes(),
                    0L, 0L));
        }

        @Override
        public void onError(String errorMessage) {
            logger.warn("Batch upload listener observed error, subtaskId={}, message={}", batchTask.getSubtaskId(),
                    errorMessage);
        }
    }
}
