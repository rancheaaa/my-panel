package com.cq.agent.batch.scheduler;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.TransferConfig;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.report.SubTaskEvent;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.client.upload.UploadListener;
import com.cq.agent.client.upload.UploadTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 批量上传监听器（spec.md 4.6）
 *
 * 功能：
 * 1. 完整的子任务生命周期管理：创建→进度→完成/失败
 * 2. 桥接AgentUploader事件到进度上报（ProgressReporter）
 * 3. 在onComplete回调中执行传输后操作（postTransferAction）
 */
public class BatchUploadListener implements UploadListener {

    private static final Logger log = LoggerFactory.getLogger(BatchUploadListener.class);
    private static final AtomicLong SUBTASK_ID_GENERATOR = new AtomicLong(0);

    private final Long taskId;
    private final FileScanner.ScannedFile scannedFile;
    private final AgentTaskConfig config;
    private final ProgressReporter progressReporter;

    /** 子任务ID（唯一标识） */
    private final Long subtaskId;

    public BatchUploadListener(Long taskId, FileScanner.ScannedFile scannedFile,
                                AgentTaskConfig config, ProgressReporter progressReporter) {
        this.taskId = taskId;
        this.scannedFile = scannedFile;
        this.config = config;
        this.progressReporter = progressReporter;
        this.subtaskId = SUBTASK_ID_GENERATOR.incrementAndGet();

        // 立即创建子任务到Proxy数据库
        createSubTaskOnProxy();
    }

    /**
     * 创建子任务并上报到Proxy（INSERT到batch_transfer_subtask表）
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
    public void onProgress(int totalChunks, int uploadedChunks, double progress) {
        log.debug("📊 上传进度: subtask={}, file={}, {}/{} ({}%)",
            subtaskId, scannedFile.getFileName(), uploadedChunks, totalChunks, progress);

        if (progressReporter != null) {
            SubTaskEvent event = buildBaseEvent("SENDING");
            event.setTransferredChunks(uploadedChunks);
            event.setTotalChunks(totalChunks);
            event.setTransferredBytes((long) (scannedFile.getFileSize() * progress));
            event.setSpeedBytesPerSec(calculateSpeedBytesPerSec(uploadedChunks, totalChunks, progress));

            progressReporter.reportSubTaskStatus(event);
        }
    }

    @Override
    public void onComplete(UploadTask task) {
        log.info("✅ 文件上传完成: subtask={}, file={}, transferId={}",
            subtaskId, scannedFile.getFileName(), task.getTransferId());

        if (progressReporter != null) {
            SubTaskEvent event = buildBaseEvent("COMPLETED");
            event.setTransferId(task.getTransferId());
            event.setTransferredChunks(1);
            event.setTotalChunks(1);
            event.setTransferredBytes(scannedFile.getFileSize());
            event.setSpeedBytesPerSec(null); // 传输完成时速度为null
            event.setCompletedAt(new Date());

            progressReporter.reportSubTaskStatus(event);
        }

        executePostTransferAction();
    }

    @Override
    public void onError(String errorMessage) {
        log.error("❌ 文件上传失败: subtask={}, file={}, error={}",
            subtaskId, scannedFile.getFileName(), errorMessage);

        if (progressReporter != null) {
            SubTaskEvent event = buildBaseEvent("FAILED");
            event.setErrorCode("UPLOAD_ERROR");
            event.setErrorMessage(errorMessage);

            progressReporter.reportSubTaskStatus(event);
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
        event.setStartedAt(new Date());
        return event;
    }

    /**
     * 计算传输速度（字节/秒）- 简化估算
     */
    private Long calculateSpeedBytesPerSec(int uploadedChunks, int totalChunks, double progress) {
        if (scannedFile.getFileSize() <= 0 || progress <= 0) return null;

        long transferredBytes = (long) (scannedFile.getFileSize() * progress);
        return transferredBytes; // 简化实现，实际应基于时间计算
    }

    /**
     * 获取第一个目标Agent ID
     */
    private String getFirstTargetAgentId() {
        if (config == null || config.getTargetAgents() == null
                || config.getTargetAgents().isEmpty()) return null;
        return config.getTargetAgents().get(0).getAgentId();
    }

    /**
     * 获取第一个目标Agent名称
     */
    private String getFirstTargetAgentName() {
        if (config == null || config.getTargetAgents() == null
                || config.getTargetAgents().isEmpty()) return null;
        return config.getTargetAgents().get(0).getAgentName();
    }

    /**
     * 执行传输后操作（spec.md 4.6 - postTransferAction）
     */
    private void executePostTransferAction() {
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
