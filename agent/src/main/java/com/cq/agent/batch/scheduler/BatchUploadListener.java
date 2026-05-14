package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.scanner.ScannedFile;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.TransferConfig;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.report.SubTaskEvent;
import com.cq.agent.client.upload.UploadListener;
import com.cq.agent.client.upload.UploadTask;
import lombok.Getter;
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
 * - POST /api/batch/subtask/create    → 子任务创建（QUEUED状态）
 * - POST /api/batch/subtask/progress  → 传输进度上报（SENDING状态，分块级进度）
 * - POST /api/batch/subtask/complete  → 传输完成上报（COMPLETED状态）
 * - POST /api/batch/subtask/failed    → 传输失败上报（FAILED状态）
 * - POST /api/batch/subtask/retrying  → 重试状态上报（RETRYING状态）
 */
public class BatchUploadListener implements UploadListener {

    private static final Logger log = LoggerFactory.getLogger(BatchUploadListener.class);

    /**
     * -- GETTER --
     *  获取任务ID
     */
    @Getter
    private Long taskId;
    private final ScannedFile scannedFile;
    private final AgentTaskConfig config;
    private final ProgressReporter progressReporter;

    /** 子任务ID（全局唯一：基于taskId + fileName哈希 + 时间戳）
     * -- GETTER --
     *  获取子任务ID
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
    private long lastTransferredBytes = 0;
    private long lastUpdateTime = System.currentTimeMillis();

    /** 记录实际的分块数量 */
    private Integer actualTotalChunks = null;

    /** 传输开始时间戳（毫秒） */
    private Long transferStartTime = null;

    /**
     * 有参构造函数 - 正常创建新任务时使用
     */
    public BatchUploadListener(Long taskId, ScannedFile scannedFile,
            AgentTaskConfig config, ProgressReporter progressReporter) {
        this.taskId = taskId;
        this.scannedFile = scannedFile;
        this.config = config;
        this.progressReporter = progressReporter;

        // 生成全局唯一的subtaskId（避免重启后冲突）
        this.subtaskId = generateUniqueSubtaskId(taskId, scannedFile.getFileName());

        // 立即创建子任务到Proxy数据库
        createSubTaskOnProxy();
        log.info("✅ 新建上传监听器: subtaskId={}, file={}", subtaskId, scannedFile.getFileName());
    }

    /**
     * 生成全局唯一的子任务ID
     * 使用taskId + fileName哈希 + 时间戳 + 随机数确保唯一性
     */
    private static Long generateUniqueSubtaskId(Long taskId, String fileName) {
        long hashPart = Objects.hash(taskId, fileName) & 0xFFFFFFFFL; // 取低32位
        long timePart = (System.currentTimeMillis() / 1000) << 32; // 时间戳（秒级）左移32位
        long randomPart = ThreadLocalRandom.current().nextInt(0, 10000); // 随机数0-9999

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
            event.setStatus("QUEUED");

            // Agent信息
            if (config != null) {
                event.setSourceAgentId(config.getSourceAgentId());
                event.setSourceAgentName(config.getSourceAgentName());
                event.setTargetAgentId(getFirstTargetAgentId());
                event.setTargetAgentName(getFirstTargetAgentName());
            }

            // 文件信息
            event.setSourcePath(scannedFile.getAbsolutePath());
            event.setFileName(scannedFile.getFileName());
            event.setFileSizeBytes(scannedFile.getFileSize());
            event.setFileLastModified(new Date(scannedFile.getLastModified()));

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

        log.debug("📝 onBeforeSend已设置任务信息: transferId={}, taskId={}, subtaskId={}, fileName={}",
                task.getTransferId(), taskId, subtaskId,
                scannedFile != null ? scannedFile.getFileName() : "null");
    }

    @Override
    public void onProgress(int totalChunks, int uploadedChunks, double progress) {
        if (scannedFile == null || subtaskId == null) {
            log.warn("⚠️ 状态不完整，跳过进度上报: subtaskId={}, scannedFile={}", subtaskId, scannedFile);
            return;
        }

        log.debug("📊 上传进度: subtask={}, file={}, {}/{} ({}%)",
                subtaskId, scannedFile.getFileName(), uploadedChunks, totalChunks, progress);

        // 记录实际的分块数量（第一次调用时）
        if (actualTotalChunks == null && totalChunks > 0) {
            actualTotalChunks = totalChunks;
        }

        // 记录传输开始时间（第一次进度回调时）
        if (transferStartTime == null) {
            transferStartTime = System.currentTimeMillis();
        }

        if (progressReporter != null) {
            SubTaskEvent event = buildProgressEvent();
            event.setTransferredChunks(uploadedChunks);
            event.setTotalChunks(totalChunks);
            event.setTransferredBytes((long) (scannedFile.getFileSize() * progress));
            event.setSpeedBytesPerSec(calculateSpeedBytesPerSec(progress));

            // 使用POST /api/batch/subtask/progress接口
            progressReporter.reportProgress(event);
        }
    }

    @Override
    public void onComplete(UploadTask task) {
        if (scannedFile == null || subtaskId == null) {
            log.warn("⚠️ 状态不完整，跳过完成通知: subtaskId={}, scannedFile={}", subtaskId, scannedFile);
            return;
        }

        log.info("✅ 文件上传完成: subtask={}, file={}, transferId={}",
                subtaskId, scannedFile.getFileName(), task.getTransferId());

        if (progressReporter != null) {
            SubTaskEvent event = buildCompleteEvent(task);

            // 使用POST /api/batch/subtask/complete接口
            progressReporter.reportComplete(event);
        }

        executePostTransferAction();
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
            event.setTargetAgentId(getFirstTargetAgentId());
            event.setTargetAgentName(getFirstTargetAgentName());
        }

        // 文件信息
        if (scannedFile != null) {
            event.setSourcePath(scannedFile.getAbsolutePath());
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
        SubTaskEvent event = buildBaseEvent("SENDING");
        event.setStartedAt(transferStartTime != null ? new Date(transferStartTime) : new Date());
        return event;
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
        event.setSpeedBytesPerSec(null);

        long startTime = transferStartTime != null ? transferStartTime : System.currentTimeMillis();
        long completedTime = System.currentTimeMillis();
        event.setStartedAt(new Date(startTime));
        event.setCompletedAt(new Date(completedTime));
        event.setDurationMs(completedTime - startTime);

        return event;
    }

    /**
     * 构建失败事件（用于/failed接口）
     */
    private SubTaskEvent buildFailEvent(String errorMessage) {
        SubTaskEvent event = buildBaseEvent("FAILED");
        event.setErrorCode("UPLOAD_ERROR");
        event.setErrorMessage(errorMessage);

        if (transferStartTime != null) {
            event.setStartedAt(new Date(transferStartTime));
            event.setDurationMs(System.currentTimeMillis() - transferStartTime);
        }

        return event;
    }

    /**
     * 构建重试事件（用于/retrying接口）
     * 从RetryAwareUploaderDecorator调用
     */
    public SubTaskEvent buildRetryingEvent(int retryCount) {
        SubTaskEvent event = buildBaseEvent("RETRYING");
        event.setRetryCount(retryCount);
        event.setLastRetryAt(new Date());
        event.setNextRetryAfter(new Date(calculateNextRetryAfter(retryCount)));
        return event;
    }

    /**
     * 上报重试事件
     */
    public void reportRetrying(int retryCount) {
        if (progressReporter != null) {
            SubTaskEvent event = buildRetryingEvent(retryCount);
            // 使用POST /api/batch/subtask/retrying接口
            progressReporter.reportRetrying(event);
        }
    }

    /**
     * 计算下次重试时间（毫秒时间戳）
     */
    private Long calculateNextRetryAfter(int retryCount) {
        if (config == null || config.getRetryConfig() == null || !config.getRetryConfig().isEnabled()) {
            return System.currentTimeMillis() + 30 * 60 * 1000L; // 默认30分钟
        }

        int intervalMin = config.getRetryConfig().getIntervalMin() != null
                ? config.getRetryConfig().getIntervalMin() : 30;
        String backoffType = config.getRetryConfig().getBackoffType();

        long baseIntervalMs = intervalMin * 60_000L;
        long delay;

        if ("EXPONENTIAL".equalsIgnoreCase(backoffType)) {
            delay = baseIntervalMs * (long) Math.pow(2, retryCount);
        } else if ("LINEAR".equalsIgnoreCase(backoffType)) {
            delay = baseIntervalMs * (retryCount + 1);
        } else {
            delay = baseIntervalMs;
        }

        return System.currentTimeMillis() + delay;
    }

    /**
     * 计算传输速度（字节/秒）- 基于时间差的瞬时速度
     */
    private Long calculateSpeedBytesPerSec(double progress) {
        if (scannedFile == null || scannedFile.getFileSize() <= 0 || progress <= 0)
            return null;

        long currentTransferredBytes = (long) (scannedFile.getFileSize() * progress);
        long currentTime = System.currentTimeMillis();

        // 首次调用时，只记录不返回速度
        if (lastTransferredBytes == 0 && progress < 1.0) {
            lastTransferredBytes = currentTransferredBytes;
            lastUpdateTime = currentTime;
            return null;
        }

        long timeDiffMs = currentTime - lastUpdateTime;
        if (timeDiffMs == 0)
            return null;

        long bytesDiff = currentTransferredBytes - lastTransferredBytes;
        long speedBytesPerSec = bytesDiff * 1000 / timeDiffMs;

        // 更新状态（只在进度增加时更新）
        if (bytesDiff > 0) {
            lastTransferredBytes = currentTransferredBytes;
            lastUpdateTime = currentTime;
        }

        return speedBytesPerSec > 0 ? speedBytesPerSec : null;
    }

    /**
     * 获取第一个目标Agent ID
     */
    private String getFirstTargetAgentId() {
        if (config == null || config.getTargetAgents() == null
                || config.getTargetAgents().isEmpty())
            return null;
        return config.getTargetAgents().getFirst().getAgentId();
    }

    /**
     * 获取第一个目标Agent名称
     */
    private String getFirstTargetAgentName() {
        if (config == null || config.getTargetAgents() == null
                || config.getTargetAgents().isEmpty())
            return null;
        return config.getTargetAgents().getFirst().getAgentName();
    }

    /**
     * 执行传输后操作（spec.md 4.6 - postTransferAction）
     */
    private void executePostTransferAction() {
        if (scannedFile == null) {
            log.warn("⚠️ scannedFile为null，跳过传输后操作");
            return;
        }

        String postTransferAction = "NONE";
        if (config != null && config.getTransferConfig() != null
                && config.getTransferConfig().getPostTransferAction() != null) {
            postTransferAction = config.getTransferConfig().getPostTransferAction();
        }

        if ("NONE".equalsIgnoreCase(postTransferAction)) {
            log.debug("ℹ️ postTransferAction=NONE，跳过后续操作: subtask={}, fileName={}",
                    subtaskId, scannedFile.getFileName());
            return;
        }

        log.info("🔧 执行传输后操作: subtask={}, fileName={}, action={}",
                subtaskId, scannedFile.getFileName(), postTransferAction);

        try {
            String filePath = scannedFile.getAbsolutePath();
            Path sourcePath = Paths.get(filePath);

            switch (postTransferAction.toUpperCase()) {
                case "DELETE":
                    deleteSourceFile(sourcePath);
                    break;

                case "BACKUP":
                    backupSourceFile(sourcePath);
                    break;

                default:
                    log.warn("⚠️ 未知的postTransferAction: {}, 跳过", postTransferAction);
                    break;
            }

            log.info("✅ 传输后操作成功: subtask={}, fileName={}, action={}",
                    subtaskId, scannedFile.getFileName(), postTransferAction);

        } catch (Exception e) {
            log.error("❌ 执行postTransferAction失败: subtask={}, fileName={}, error={}",
                    subtaskId, scannedFile.getFileName(), e.getMessage());
        }
    }

    /**
     * 删除源文件
     */
    private void deleteSourceFile(Path sourcePath) {
        try {
            if (!Files.exists(sourcePath)) {
                log.warn("⚠️ 源文件不存在，跳过删除: fileName={}", scannedFile.getFileName());
                return;
            }

            Files.delete(sourcePath);
            log.info("🗑️ 源文件已删除: subtask={}, fileName={}", subtaskId, scannedFile.getFileName());

        } catch (Exception e) {
            log.error("❌ 删除源文件失败: fileName={}, error={}", scannedFile.getFileName(), e.getMessage());
            throw new RuntimeException("删除源文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 备份源文件
     */
    private void backupSourceFile(Path sourcePath) {
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
                log.info("📁 创建备份目录: {}", backupDir);
            }

            Path targetPath = backupPath.resolve(scannedFile.getFileName());

            if (Files.exists(targetPath)) {
                String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                String newName = scannedFile.getFileName() + "." + timestamp;
                targetPath = backupPath.resolve(newName);
                log.info("ℹ️ 备份文件已存在，使用新名称: {}", newName);
            }

            if ("MOVE".equalsIgnoreCase(backupMode)) {
                Files.move(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                log.info("✅ 源文件已移动到备份目录: subtask={}, src={}, dst={}",
                        subtaskId, sourcePath, targetPath);
            } else {
                Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                log.info("✅ 源文件已复制到备份目录: subtask={}, src={}, dst={}",
                        subtaskId, sourcePath, targetPath);
            }

        } catch (Exception e) {
            log.error("❌ 备份源文件失败: fileName={}, backupDir={}, error={}",
                    scannedFile.getFileName(), backupDir, e.getMessage());
            throw new RuntimeException("备份源文件失败: " + e.getMessage(), e);
        }
    }
}
