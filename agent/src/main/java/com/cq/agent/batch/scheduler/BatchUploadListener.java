package com.cq.agent.batch.scheduler;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.agent.batch.report.ProgressEvent;
import com.cq.agent.batch.report.ProgressReporter;
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

/**
 * 批量上传监听器（spec.md 4.6）
 * 
 * 功能：
 * 1. 桥接AgentUploader事件到进度上报（ProgressReporter）
 * 2. 在onComplete回调中执行传输后操作（postTransferAction）
 *    - DELETE: 删除源文件
 *    - BACKUP: 备份源文件（COPY/MOVE模式）
 *    - NONE: 不做任何处理（默认）
 */
public class BatchUploadListener implements UploadListener {

    private static final Logger log = LoggerFactory.getLogger(BatchUploadListener.class);

    private final Long taskId;
    private final FileScanner.ScannedFile scannedFile;
    private final AgentTaskConfig config;
    private final ProgressReporter progressReporter;

    public BatchUploadListener(Long taskId, FileScanner.ScannedFile scannedFile,
                                AgentTaskConfig config, ProgressReporter progressReporter) {
        this.taskId = taskId;
        this.scannedFile = scannedFile;
        this.config = config;
        this.progressReporter = progressReporter;
    }

    @Override
    public void onProgress(int totalChunks, int uploadedChunks, double progress) {
        log.debug("📊 上传进度: taskId={}, file={}, {}/{} ({}%)",
            taskId, scannedFile.getFileName(), uploadedChunks, totalChunks, progress);

        if (progressReporter != null) {
            ProgressEvent event = new ProgressEvent();
            event.setSubtaskId(taskId);
            event.setTaskId(taskId);
            event.setStatus("SENDING");
            event.setTransferredChunks(uploadedChunks);
            event.setTotalChunks(totalChunks);
            event.setTransferredBytes((long) (scannedFile.getFileSize() * progress));
            event.setTimestamp(System.currentTimeMillis());
            event.setSequenceNumber(uploadedChunks);

            progressReporter.reportProgress(event);
        }
    }

    @Override
    public void onComplete(UploadTask task) {
        log.info("✅ 文件上传完成: taskId={}, file={}, transferId={}",
            taskId, scannedFile.getFileName(), task.getTransferId());

        if (progressReporter != null) {
            ProgressEvent event = new ProgressEvent();
            event.setSubtaskId(taskId);
            event.setTaskId(taskId);
            event.setTransferId(task.getTransferId());
            event.setStatus("COMPLETED");
            event.setTransferredChunks(1);
            event.setTotalChunks(1);
            event.setTransferredBytes(scannedFile.getFileSize());
            event.setTimestamp(System.currentTimeMillis());

            progressReporter.reportProgress(event);
        }

        // 执行传输后操作（spec.md 4.6 - postTransferAction）
        executePostTransferAction();
    }

    @Override
    public void onError(String errorMessage) {
        log.error("❌ 文件上传失败: taskId={}, file={}, error={}",
            taskId, scannedFile.getFileName(), errorMessage);

        if (progressReporter != null) {
            ProgressEvent event = new ProgressEvent();
            event.setSubtaskId(taskId);
            event.setTaskId(taskId);
            event.setStatus("FAILED");
            event.setTimestamp(System.currentTimeMillis());

            progressReporter.reportProgress(event);
        }
    }

    /**
     * 执行传输后操作（spec.md 4.6 - postTransferAction）
     *
     * 支持的操作：
     * - NONE: 不做任何处理（默认）
     * - DELETE: 删除源文件
     * - BACKUP: 备份源文件到backup_dir（COPY或MOVE模式）
     */
    private void executePostTransferAction() {
        String postTransferAction = "NONE";
        if (config != null && config.getTransferConfig() != null
                && config.getTransferConfig().getPostTransferAction() != null) {
            postTransferAction = config.getTransferConfig().getPostTransferAction();
        }

        if ("NONE".equalsIgnoreCase(postTransferAction)) {
            log.debug("ℹ️ postTransferAction=NONE，跳过后续操作: taskId={}, fileName={}",
                taskId, scannedFile.getFileName());
            return;
        }

        log.info("🔧 执行传输后操作: taskId={}, fileName={}, action={}",
            taskId, scannedFile.getFileName(), postTransferAction);

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

            log.info("✅ 传输后操作成功: taskId={}, fileName={}, action={}",
                taskId, scannedFile.getFileName(), postTransferAction);

        } catch (Exception e) {
            log.error("❌ 执行postTransferAction失败: taskId={}, fileName={}, error={}",
                taskId, scannedFile.getFileName(), e.getMessage());
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
            log.info("🗑️ 源文件已删除: taskId={}, fileName={}", taskId, scannedFile.getFileName());

        } catch (Exception e) {
            log.error("❌ 删除源文件失败: fileName={}, error={}", scannedFile.getFileName(), e.getMessage());
            throw new RuntimeException("删除源文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 备份源文件
     COPY-复制（保留原文件）, MOVE-移动（删除原文件）
     */
    private void backupSourceFile(Path sourcePath) {
        String backupDir = config.getBackupDir();
        String backupMode = config.getBackupMode();

        if (backupDir == null || backupDir.isBlank()) {
            throw new IllegalStateException("BACKUP模式但backupDir未配置: taskId=" + taskId
                + ", fileName=" + scannedFile.getFileName());
        }

        try {
            Path backupPath = Paths.get(backupDir);

            // 确保备份目录存在
            if (!Files.exists(backupPath)) {
                Files.createDirectories(backupPath);
                log.info("📁 创建备份目录: {}", backupDir);
            }

            // 构建目标路径：backupDir/原文件名
            Path targetPath = backupPath.resolve(scannedFile.getFileName());

            // 如果目标文件已存在，添加时间戳避免覆盖
            if (Files.exists(targetPath)) {
                String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                String newName = scannedFile.getFileName() + "." + timestamp;
                targetPath = backupPath.resolve(newName);
                log.info("ℹ️ 备份文件已存在，使用新名称: {}", newName);
            }

            if ("MOVE".equalsIgnoreCase(backupMode)) {
                // 移动文件
                Files.move(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                log.info("✅ 源文件已移动到备份目录: taskId={}, src={}, dst={}",
                    taskId, sourcePath, targetPath);
            } else {
                // 默认复制模式
                Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                log.info("✅ 源文件已复制到备份目录: taskId={}, src={}, dst={}",
                    taskId, sourcePath, targetPath);
            }

        } catch (Exception e) {
            log.error("❌ 备份源文件失败: fileName={}, backupDir={}, error={}",
                scannedFile.getFileName(), backupDir, e.getMessage());
            throw new RuntimeException("备份源文件失败: " + e.getMessage(), e);
        }
    }
}
