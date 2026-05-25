package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.scanner.ScannedFile;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.TransferConfig;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.report.SubTaskEvent;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.client.upload.TransferFileStateManager;
import com.cq.agent.client.upload.UploadListener;
import com.cq.agent.client.upload.UploadTask;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 批量上传监听器（spec.md 4.6）
 * 功能：
 * 1. 完整的子任务生命周期管理：创建→进度→完成/失败/重试
 * 2. 桥接AgentUploader事件到进度上报（ProgressReporter）
 * 3. 在onComplete回调中执行传输后操作（postTransferAction）
 * 使用的Proxy接口：
 * - POST /api/batch/subtask/create → 子任务创建（QUEUED状态）
 * - POST /api/batch/subtask/progress → 传输进度上报（SENDING状态，分块级进度）
 * - POST /api/batch/subtask/complete → 传输完成上报（COMPLETED状态）
 * - POST /api/batch/subtask/failed → 传输失败上报（FAILED状态）
 * - POST /api/batch/subtask/retrying → 重试状态上报（RETRYING状态）
 */
public class BatchUploadListener implements UploadListener {

    private static final Logger log = LoggerFactory.getLogger(BatchUploadListener.class);

    /**
     * -- GETTER --
     * 获取任务ID
     */
    @Getter
    private Long taskId;
    private Long scanBatchId;
    private Long fileBatchId;
    private ScannedFile scannedFile;
    private AgentTaskConfig config;
    private TargetAgentInfo targetAgent;
    private ProgressReporter progressReporter;
    private String successQueueDir;
    private String sendingQueueDir;
    private FileBatchCompletionTracker fileBatchTracker;

    /**
     * 子任务ID（全局唯一：基于taskId + fileName哈希 + 时间戳）
     * -- GETTER --
     * 获取子任务ID
     */
    @Getter
    private Long subtaskId;

    /**
     * 获取文件名（从scannedFile获取）
     */
    public String getFileName() {
        return scannedFile != null ? scannedFile.getFileName() : null;
    }

    /**
     * 获取文件大小（从scannedFile获取）
     */
    public long getFileSize() {
        return scannedFile != null ? scannedFile.getFileSize() : 0L;
    }

    /** 用于计算传输速度的变量 */
    @Getter
    @Setter
    private long lastTransferredBytes = 0;
    @Getter
    @Setter
    private long lastUpdateTime = System.currentTimeMillis();

    /** 记录实际的分块数量 */
    private Integer actualTotalChunks = null;

    /** 传输开始时间戳（毫秒） */
    private Long transferStartTime = null;

    /** 子任务创建时间戳（毫秒，QUEUED阶段） */
    private Long subtaskCreateTime = null;

    /** 传输ID（从UploadTask获取） */
    private String currentTransferId = null;

    /**
     * 有参构造函数 - 正常创建新任务时使用
     * 
     * @param taskId           任务ID
     * @param scannedFile      扫描到的文件
     * @param config           任务配置
     * @param targetAgent      目标Agent信息（一个源文件发给一个目标Agent对应一个子任务）
     * @param progressReporter 进度上报器
     * @param successQueueDir  上传成功队列目录
     * @param sendingQueueDir  发送中队列目录
     * @param scanBatchId      扫描批次ID
     * @param fileBatchId      文件批次ID
     */
    public BatchUploadListener(Long taskId, ScannedFile scannedFile,
            AgentTaskConfig config, TargetAgentInfo targetAgent, ProgressReporter progressReporter,
            String successQueueDir, String sendingQueueDir,
            Long scanBatchId, Long fileBatchId,
            FileBatchCompletionTracker fileBatchTracker) {
        this.taskId = taskId;
        this.scanBatchId = scanBatchId;
        this.fileBatchId = fileBatchId;
        this.scannedFile = scannedFile;
        this.config = config;
        this.targetAgent = targetAgent;
        this.progressReporter = progressReporter;
        this.successQueueDir = successQueueDir;
        this.sendingQueueDir = sendingQueueDir;
        this.fileBatchTracker = fileBatchTracker;

        // 生成全局唯一的subtaskId（避免重启后冲突）
        this.subtaskId = generateUniqueSubtaskId(taskId, scannedFile.getFileName(),
                targetAgent != null ? targetAgent.getAgentId() : null);

        // 立即创建子任务到Proxy数据库
        createSubTaskOnProxy();
        log.info("✅ 新建上传监听器: subtaskId={}, file={}, target={}, scanBatch={}, fileBatch={}",
                subtaskId, scannedFile.getFileName(),
                targetAgent != null ? targetAgent.getAgentId() : "null",
                scanBatchId, fileBatchId);
    }

    /**
     * 测试用构造函数 - 仅用于单元测试，不触发子任务创建
     */
    BatchUploadListener(Long taskId, ScannedFile scannedFile,
            AgentTaskConfig config, TargetAgentInfo targetAgent) {
        this.taskId = taskId;
        this.scanBatchId = null;
        this.fileBatchId = null;
        this.scannedFile = scannedFile;
        this.config = config;
        this.targetAgent = targetAgent;
        this.progressReporter = null;
        this.successQueueDir = null;
        this.sendingQueueDir = null;
        this.fileBatchTracker = null;
        this.subtaskId = null;
    }

    /**
     * 重试工厂方法 - 用于失败重试场景，复用已有 subtaskId，不创建新子任务记录
     * 注意：不调用全参构造函数，避免触发createSubTaskOnProxy()创建幽灵子任务
     */
    public static BatchUploadListener forRetry(Long taskId, Long existingSubtaskId, ScannedFile scannedFile,
            AgentTaskConfig config, TargetAgentInfo targetAgent, ProgressReporter progressReporter,
            String successQueueDir, String sendingQueueDir,
            Long scanBatchId, Long fileBatchId,
            FileBatchCompletionTracker fileBatchTracker) {
        // 直接赋值，不调用构造函数（避免触发createSubTaskOnProxy）
        BatchUploadListener listener = new BatchUploadListener();
        listener.taskId = taskId;
        listener.scanBatchId = scanBatchId;
        listener.fileBatchId = fileBatchId;
        listener.scannedFile = scannedFile;
        listener.config = config;
        listener.targetAgent = targetAgent;
        listener.progressReporter = progressReporter;
        listener.successQueueDir = successQueueDir;
        listener.sendingQueueDir = sendingQueueDir;
        listener.fileBatchTracker = fileBatchTracker;
        listener.subtaskId = existingSubtaskId;

        log.info("✅ 恢复模式上传监听器(重试): subtaskId={}, file={}, target={}, scanBatch={}, fileBatch={}",
                existingSubtaskId, scannedFile.getFileName(),
                targetAgent != null ? targetAgent.getAgentId() : "null",
                scanBatchId, fileBatchId);
        return listener;
    }

    /**
     * 默认构造函数（仅供forRetry工厂方法使用）
     */
    private BatchUploadListener() {
        this.scanBatchId = null;
        this.fileBatchId = null;
        this.scannedFile = null;
        this.config = null;
        this.targetAgent = null;
        this.progressReporter = null;
        this.successQueueDir = null;
        this.sendingQueueDir = null;
        this.fileBatchTracker = null;
    }

    /**
     * 测试用方法 - 暴露computeTargetPath供单元测试调用
     */
    String computeTargetPathForTest() {
        return computeTargetPath();
    }

    /**
     * 生成全局唯一的子任务ID
     * 使用taskId + fileName哈希 + 时间戳 + 随机数确保唯一性
     */
    private static Long generateUniqueSubtaskId(Long taskId, String fileName, String targetAgentId) {
        long hashPart = Objects.hash(taskId, fileName, targetAgentId) & 0xFFFFFFFFL;
        long timePart = (System.currentTimeMillis() / 1000) << 32;
        long randomPart = ThreadLocalRandom.current().nextInt(0, 10000);

        return timePart | hashPart | randomPart;
    }

    /**
     * 创建子任务并上报到Proxy（INSERT到batch_transfer_subtask表）
     * 使用接口：POST /api/batch/subtask/create
     */
    private void createSubTaskOnProxy() {
        if (progressReporter == null) {
            log.debug("ProgressReporter未设置，跳过子任务创建");
            return;
        }

        try {
            SubTaskEvent event = new SubTaskEvent();
            event.setSubtaskId(subtaskId);
            event.setTaskId(taskId);
            event.setScanBatchId(scanBatchId);
            event.setFileBatchId(fileBatchId);
            event.setStatus("QUEUED");

            // Agent信息
            if (config != null) {
                event.setSourceAgentId(config.getSourceAgentId());
                event.setSourceAgentName(config.getSourceAgentName());
            }
            if (targetAgent != null) {
                event.setTargetAgentId(targetAgent.getAgentId());
                event.setTargetAgentName(targetAgent.getAgentName());
            }

            // 文件信息（使用原始路径上报，不使用隐藏文件路径）
            event.setSourcePath(scannedFile.getOriginalAbsolutePath());
            event.setTargetPath(computeTargetPath());
            event.setFileName(scannedFile.getFileName());
            event.setFileSizeBytes(scannedFile.getFileSize());
            event.setFileLastModified(new Date(scannedFile.getLastModified()));

            if (config != null && config.getTargetAgents() != null) {
                event.setTargetCount(config.getTargetAgents().size());
            }

            // 记录子任务创建时间，作为startedAt的兜底
            long now = System.currentTimeMillis();
            this.subtaskCreateTime = now;
            event.setStartedAt(new Date(now));

            boolean success = progressReporter.createSubTask(event);
            if (success) {
                log.info("📝 子任务已创建: subtaskId={}, file={}, size={}bytes",
                        subtaskId, scannedFile.getFileName(), scannedFile.getFileSize());
            } else {
                log.warn("⚠️ 子任务创建失败: file={}", scannedFile.getFileName());
            }
        } catch (Exception e) {
            log.error("❌ 子任务创建异常: file={}, error={}", scannedFile.getFileName(), e.getMessage());
        }
    }

    @Override
    public void onBeforeSend(UploadTask task) {
        if (task == null) {
            log.warn("⚠️ UploadTask为空，跳过onBeforeSend处理");
            return;
        }

        if (taskId != null && task.getTaskId() == null) {
            task.setTaskId(taskId);
        }

        if (subtaskId != null && task.getSubtaskId() == null) {
            task.setSubtaskId(subtaskId);
        }

        if (scanBatchId != null && task.getScanBatchId() == null) {
            task.setScanBatchId(scanBatchId);
        }

        if (fileBatchId != null && task.getFileBatchId() == null) {
            task.setFileBatchId(fileBatchId);
        }

        if (task.getTransferId() != null) {
            this.currentTransferId = task.getTransferId();
        }

        if (transferStartTime == null) {
            transferStartTime = System.currentTimeMillis();
        }

        if (scannedFile != null) {
            String fileName = scannedFile.getFileName();
            long fileSize = scannedFile.getFileSize();

            if (fileName != null && task.getFileName() == null) {
                task.setFileName(fileName);
            }

            if (fileSize > 0 && task.getFileSize() <= 0) {
                task.setFileSize(fileSize);
            }
        }

        log.debug(
                "📝 onBeforeSend已设置任务信息: transferId={}, taskId={}, subtaskId={}, scanBatch={}, fileBatch={}, fileName={}",
                task.getTransferId(), taskId, subtaskId, scanBatchId, fileBatchId,
                scannedFile != null ? scannedFile.getFileName() : "null");
    }

    @Override
    public void onProgress(int totalChunks, int uploadedChunks, double progress) {
        if (scannedFile == null || subtaskId == null) {
            log.warn("️ 状态不完整，跳过进度上报: subtaskId={}, scannedFile={}", subtaskId, scannedFile);
            return;
        }

        // progress来自AgentUploader，是0-100的百分比，需转为0-1的比例
        double clampedProgress = Math.min(1.0, Math.max(0.0, progress / 100.0));

        log.debug(" 上传进度: subtask={}, file={}, {}/{} ({}%)",
                subtaskId, scannedFile.getFileName(), uploadedChunks, totalChunks,
                String.format("%.1f", clampedProgress * 100));

        if (actualTotalChunks == null && totalChunks > 0) {
            actualTotalChunks = totalChunks;
        }

        if (progressReporter != null) {
            SubTaskEvent event = buildProgressEvent();
            event.setTransferredChunks(uploadedChunks);
            event.setTotalChunks(totalChunks);
            long transferredBytes = (long) (scannedFile.getFileSize() * clampedProgress);
            event.setTransferredBytes(transferredBytes);
            event.setTotalBytes(scannedFile.getFileSize());
            event.setSpeedBytesPerSec(calculateSpeedBytesPerSec(clampedProgress));
            if (currentTransferId != null) {
                event.setTransferId(currentTransferId);
            }

            progressReporter.reportProgress(event);
        }
    }

    @Override
    public void onComplete(UploadTask task) {
        if (scannedFile == null || subtaskId == null) {
            log.warn("状态不完整，跳过完成通知: subtaskId={}, scannedFile={}", subtaskId, scannedFile);
            return;
        }

        log.info("文件上传完成: subtask={}, file={}, transferId={}",
                subtaskId, scannedFile.getFileName(), task.getTransferId());

        if (progressReporter != null) {
            SubTaskEvent event = buildCompleteEvent(task);

            // 使用POST /api/batch/subtask/complete接口
            progressReporter.reportComplete(event);
        }
        if (fileBatchTracker != null && fileBatchId != null) {
            try {
                final boolean result = fileBatchTracker.fileBatchIdExist(fileBatchId);
                if (!result) {
                    // 可能是一对一的传输任务。
                    executePostTransferAction();
                } else {
                    boolean allDone = fileBatchTracker.markCompleted(fileBatchId,
                            targetAgent != null ? targetAgent.getAgentId() : null);
                    if (allDone) {
                        executePostTransferAction();
                        try {
                            fileBatchTracker.deleteFileBatch(fileBatchId);
                        } catch (Exception e) {
                            log.warn("删除文件批次追踪文件失败: fileBatchId={}, error={}", fileBatchId, e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                // markCompleted异常时无法确定是否所有目标都已完成，
                // 不应调用executePostTransferAction（可能过早删除/恢复源文件导致其他目标无法传输）
                // 仅记录错误，等待下次扫描或人工干预
                log.error("❌ 标记文件批次完成状态异常: fileBatchId={}, error={}", fileBatchId, e.getMessage());
            }
        } else {
            // 非批量场景（单目标），直接执行传输后操作
            executePostTransferAction();
        }
        moveControlFileToSuccessQueue();
    }

    @Override
    public void onError(String errorMessage) {
        if (subtaskId == null) {
            log.error("❌ 状态不完整，无法上报错误: subtaskId=null, error={}", errorMessage);
            return;
        }

        String fileName = scannedFile != null ? scannedFile.getFileName() : "unknown";
        log.error("❌ 文件上传失败: subtask={}, file={}, error={}",
                subtaskId, fileName, errorMessage);

        if (progressReporter != null) {
            SubTaskEvent event = buildFailEvent(errorMessage);

            // 使用POST /api/batch/subtask/failed接口
            progressReporter.reportFailed(event);
        }
        if (fileBatchTracker != null && fileBatchId != null) {
            try {
                fileBatchTracker.markFailed(fileBatchId,
                        targetAgent != null ? targetAgent.getAgentId() : null,
                        false);
            } catch (Exception e) {
                log.warn("⚠️ 标记文件批次失败状态异常: fileBatchId={}, error={}", fileBatchId, e.getMessage());
            }
        }
    }

    /**
     * 构建基础事件（包含公共字段）
     */
    private SubTaskEvent buildBaseEvent(String status) {
        SubTaskEvent event = new SubTaskEvent();
        event.setSubtaskId(subtaskId);
        event.setTaskId(taskId);
        event.setStatus(status);

        // Agent信息
        if (config != null) {
            event.setSourceAgentId(config.getSourceAgentId());
            event.setSourceAgentName(config.getSourceAgentName());
        }
        if (targetAgent != null) {
            event.setTargetAgentId(targetAgent.getAgentId());
            event.setTargetAgentName(targetAgent.getAgentName());
        }

        // 文件信息（使用原始路径上报，不使用隐藏文件路径）
        if (scannedFile != null) {
            event.setSourcePath(scannedFile.getOriginalAbsolutePath());
            event.setTargetPath(computeTargetPath());
            event.setFileName(scannedFile.getFileName());
            event.setFileSizeBytes(scannedFile.getFileSize());
            event.setFileLastModified(new Date(scannedFile.getLastModified()));
        }

        return event;
    }

    /**
     * 构建进度事件（用于/progress接口）
     */
    private SubTaskEvent buildProgressEvent() {
        return buildBaseEvent("SENDING");
    }

    /**
     * 构建完成事件（用于/complete接口）
     */
    private SubTaskEvent buildCompleteEvent(UploadTask task) {
        SubTaskEvent event = buildBaseEvent("COMPLETED");
        event.setTransferId(task != null ? task.getTransferId() : null);

        // 使用实际的分块数量，如果从未收到onProgress则默认为1
        int finalChunks = actualTotalChunks != null ? actualTotalChunks : 1;
        event.setTransferredChunks(finalChunks);
        event.setTotalChunks(finalChunks);
        event.setTransferredBytes(scannedFile.getFileSize());
        event.setTotalBytes(scannedFile.getFileSize());

        long startTime = transferStartTime != null ? transferStartTime : System.currentTimeMillis();
        long completedTime = System.currentTimeMillis();
        long durationMs = completedTime - startTime;
        event.setCompletedAt(new Date(completedTime));
        event.setDurationMs(durationMs);

        // 计算平均传输速度：即使传输很快（durationMs很小），也能正确计算
        Long speed = durationMs > 0 ? scannedFile.getFileSize() * 1000L / durationMs : null;
        event.setSpeedBytesPerSec(speed);

        return event;
    }

    /**
     * 构建失败事件（用于/failed接口）
     */
    @SuppressWarnings("all")
    private SubTaskEvent buildFailEvent(String errorMessage) {
        SubTaskEvent event = buildBaseEvent("FAILED");
        event.setErrorCode("UPLOAD_ERROR");
        event.setErrorMessage(errorMessage);

        long completedTime = System.currentTimeMillis();
        long startTime;
        if (transferStartTime != null) {
            startTime = transferStartTime;
        } else if (subtaskCreateTime != null) {
            startTime = subtaskCreateTime;
        } else {
            startTime = completedTime;
        }

        event.setCompletedAt(new Date(completedTime));
        event.setDurationMs(completedTime - startTime);

        return event;
    }

    /**
     * 计算传输速度（字节/秒）- 基于时间差的瞬时速度
     */
    private Long calculateSpeedBytesPerSec(double progress) {
        if (scannedFile == null || scannedFile.getFileSize() <= 0 || progress <= 0)
            return null;

        long currentTransferredBytes = (long) (scannedFile.getFileSize() * progress);
        long currentTime = System.currentTimeMillis();

        if (transferStartTime == null)
            return null;

        long elapsedMs = currentTime - transferStartTime;
        if (elapsedMs <= 0)
            return null;

        return currentTransferredBytes * 1000L / elapsedMs;
    }

    private String computeTargetPath() {
        if (targetAgent == null || scannedFile == null) {
            return null;
        }
        String sourceDir = config != null ? config.getSourceDir() : null;
        String originalPath = scannedFile.getOriginalAbsolutePath();
        String fileName = scannedFile.getFileName();
        String targetDir = targetAgent.getTargetDir();
        TransferConfig transferConfig = config != null ? config.getTransferConfig() : null;

        String result = TargetPathComputer.compute(sourceDir, originalPath, fileName, targetDir, transferConfig);
        log.debug("computeTargetPath: sourceDir={}, sourceFile={}, targetPath={}", sourceDir, originalPath, result);
        return result;
    }

    /**
     * 传输成功后，将uploadSendingQueue目录下当前transferId对应的控制文件
     * 移动到uploadSuccessQueue目录下（如果目录不存在则新建）
     */
    private void moveControlFileToSuccessQueue() {
        if (sendingQueueDir == null || sendingQueueDir.isBlank()
                || successQueueDir == null || successQueueDir.isBlank()) {
            log.debug("sendingQueueDir或successQueueDir未配置，跳过控制文件移动");
            return;
        }

        try {
            Path sendingDir = Paths.get(sendingQueueDir);
            Path successDir = Paths.get(successQueueDir);

            if (!Files.exists(sendingDir)) {
                log.debug("上传发送队列目录不存在，跳过控制文件移动: {}", sendingQueueDir);
                return;
            }

            if (!Files.exists(successDir)) {
                Files.createDirectories(successDir);
                log.info("📁 创建上传成功队列目录: {}", successQueueDir);
            }

            // 只移动当前transferId对应的控制文件，避免误移动其他并发传输的文件
            String transferId = currentTransferId;
            if (transferId == null) {
                log.debug("currentTransferId为空，使用通配符模式移动");
                try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(sendingDir,
                        "UPLOAD_SUCCESS-upload-*.json")) {
                    for (Path controlFile : stream) {
                        Path targetPath = successDir.resolve(controlFile.getFileName());
                        Files.move(controlFile, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        log.info("✅ 控制文件已移动到成功队列: {} -> {}", controlFile, targetPath);
                    }
                }
            } else {
                // 精确匹配当前transferId的控制文件
                String globPattern = "UPLOAD_SUCCESS-upload-*" + transferId + "*.json";
                boolean moved = false;
                try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(sendingDir, globPattern)) {
                    for (Path controlFile : stream) {
                        Path targetPath = successDir.resolve(controlFile.getFileName());
                        Files.move(controlFile, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        log.info("✅ 控制文件已移动到成功队列: {} -> {}", controlFile, targetPath);
                        moved = true;
                    }
                }
                if (!moved) {
                    log.debug("未找到transferId={}对应的成功控制文件", transferId);
                }
            }
        } catch (Exception e) {
            log.error("❌ 移动控制文件到成功队列失败: error={}", e.getMessage());
        }
    }

    /**
     * 执行传输后操作（spec.md 4.6 - postTransferAction）
     * 适配隐藏文件机制：文件在传输前被重命名为隐藏文件（.{name}.transferring），
     * 传输完成后根据配置执行不同操作：
     * - NONE: 将隐藏文件恢复为原始文件名（unhide）
     * - DELETE: 删除隐藏文件
     * - BACKUP: 将隐藏文件备份（使用原始文件名），然后删除隐藏文件
     */
    private void executePostTransferAction() {
        if (scannedFile == null) {
            log.warn("scannedFile为null，跳过传输后操作");
            return;
        }

        String postTransferAction = "NONE";
        if (config != null && config.getTransferConfig() != null
                && config.getTransferConfig().getPostTransferAction() != null) {
            postTransferAction = config.getTransferConfig().getPostTransferAction();
        }

        if ("NONE".equalsIgnoreCase(postTransferAction)) {
            log.debug("postTransferAction=NONE，恢复原始文件: subtask={}, fileName={}",
                    subtaskId, scannedFile.getFileName());
            unhideTransferringFile();
            return;
        }

        log.info("执行传输后操作: subtask={}, fileName={}, action={}",
                subtaskId, scannedFile.getFileName(), postTransferAction);

        try {
            // 获取当前文件路径（可能是隐藏路径）
            Path currentPath = Paths.get(scannedFile.getAbsolutePath());
            // 获取原始文件路径
            Path originalPath = Paths.get(scannedFile.getOriginalAbsolutePath());

            switch (postTransferAction.toUpperCase()) {
                case "DELETE":
                    deleteSourceFile(currentPath);
                    break;

                case "BACKUP":
                    backupSourceFile(currentPath, originalPath);
                    break;

                default:
                    log.warn("未知的postTransferAction: {}, 恢复原始文件", postTransferAction);
                    unhideTransferringFile();
                    break;
            }

            log.info("传输后操作成功: subtask={}, fileName={}, action={}",
                    subtaskId, scannedFile.getFileName(), postTransferAction);

        } catch (Exception e) {
            log.error("执行postTransferAction失败: subtask={}, fileName={}, error={}",
                    subtaskId, scannedFile.getFileName(), e.getMessage());
        }
    }

    /**
     * 将隐藏文件恢复为原始文件名（取消传输中标记）
     */
    private void unhideTransferringFile() {
        Path currentPath = Paths.get(scannedFile.getAbsolutePath());
        if (TransferFileStateManager.isTransferringFile(currentPath)) {
            Path restoredPath = TransferFileStateManager.unhideFileSafely(currentPath);
            if (restoredPath != null) {
                log.info("隐藏文件已恢复: subtask={}, {} -> {}",
                        subtaskId, currentPath.getFileName(), restoredPath.getFileName());
            }
        }
    }

    /**
     * 删除源文件（支持隐藏文件路径）
     */
    private void deleteSourceFile(Path sourcePath) {
        try {
            if (!Files.exists(sourcePath)) {
                log.warn("源文件不存在，跳过删除: fileName={}", scannedFile.getFileName());
                return;
            }

            Files.delete(sourcePath);
            log.info("源文件已删除: subtask={}, fileName={}, path={}", subtaskId, scannedFile.getFileName(), sourcePath);

        } catch (Exception e) {
            log.error("删除源文件失败: fileName={}, error={}", scannedFile.getFileName(), e.getMessage());
            throw new RuntimeException("删除源文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 备份源文件（支持隐藏文件路径，使用原始文件名备份）
     *
     * @param currentPath  当前文件路径（可能是隐藏文件路径）
     * @param originalPath 原始文件路径（用于确定备份文件名）
     */
    private void backupSourceFile(Path currentPath, Path originalPath) {
        TransferConfig transferConfig = config != null ? config.getTransferConfig() : null;
        String backupDir = transferConfig != null ? transferConfig.getBackupDir() : null;
        String backupMode = transferConfig != null ? transferConfig.getBackupMode() : null;

        if (backupDir == null || backupDir.isBlank()) {
            throw new IllegalStateException("BACKUP模式但backupDir未配置: subtask=" + subtaskId
                    + ", fileName=" + scannedFile.getFileName());
        }

        try {
            Path backupPath = Paths.get(backupDir);

            if (!Files.exists(backupPath)) {
                Files.createDirectories(backupPath);
                log.info("创建备份目录: {}", backupDir);
            }

            // 使用原始文件名作为备份文件名
            String originalFileName = originalPath.getFileName().toString();
            Path targetPath = backupPath.resolve(originalFileName);

            if (Files.exists(targetPath)) {
                String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                String newName = originalFileName + "." + timestamp;
                targetPath = backupPath.resolve(newName);
                log.info("备份文件已存在，使用新名称: {}", newName);
            }

            if ("MOVE".equalsIgnoreCase(backupMode)) {
                Files.move(currentPath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                log.info("源文件已移动到备份目录: subtask={}, src={}, dst={}",
                        subtaskId, currentPath, targetPath);
            } else {
                Files.copy(currentPath, targetPath, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                log.info("源文件已复制到备份目录: subtask={}, src={}, dst={}",
                        subtaskId, currentPath, targetPath);
                // COPY模式下，复制完成后删除隐藏文件
                Files.deleteIfExists(currentPath);
                log.info("已删除隐藏源文件: subtask={}, path={}", subtaskId, currentPath);
            }

        } catch (Exception e) {
            log.error("备份源文件失败: fileName={}, backupDir={}, error={}",
                    scannedFile.getFileName(), backupDir, e.getMessage());
            throw new RuntimeException("备份源文件失败: " + e.getMessage(), e);
        }
    }

}
