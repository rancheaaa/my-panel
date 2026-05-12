package com.cq.agent.client.upload;

import com.cq.agent.client.TransferMetaStore;
import com.cq.agent.client.download.DownloadTask;
import com.cq.agent.client.download.DownloadTaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TransferMetaStore - 发起方任务元数据管理")
class TransferMetaStoreTest {

    @TempDir
    Path tempDir;

    private TransferMetaStore<UploadTask> uploadMetaStore;
    private TransferMetaStore<DownloadTask> downloadMetaStore;

    @BeforeEach
    void setUp() throws IOException {
        Path uploadDir = tempDir.resolve("uploads");
        Path downloadDir = tempDir.resolve("downloads");
        Files.createDirectories(uploadDir);
        Files.createDirectories(downloadDir);

        uploadMetaStore = new TransferMetaStore<>(uploadDir, UploadTask.class);
        downloadMetaStore = new TransferMetaStore<>(downloadDir, DownloadTask.class);
    }

    @AfterEach
    void tearDown() {
        uploadMetaStore = null;
        downloadMetaStore = null;
    }

    @Test
    @DisplayName("应该成功保存和加载上传任务")
    void shouldSaveAndLoadUploadTask() throws IOException {
        UploadTask task = createUploadTask("transfer-123");

        uploadMetaStore.saveTask(task);

        Optional<UploadTask> loaded = uploadMetaStore.loadTask("transfer-123");
        assertTrue(loaded.isPresent(), "任务应该存在");
        assertEquals(task.getLocalFilePath(), loaded.get().getLocalFilePath());
        assertEquals(task.getRemoteTargetPath(), loaded.get().getRemoteTargetPath());
        assertEquals(task.getTotalSize(), loaded.get().getTotalSize());
    }

    @Test
    @DisplayName("应该成功保存和加载下载任务")
    void shouldSaveAndLoadDownloadTask() throws IOException {
        DownloadTask task = createDownloadTask("download-456");

        downloadMetaStore.saveTask(task);

        Optional<DownloadTask> loaded = downloadMetaStore.loadTask("download-456");
        assertTrue(loaded.isPresent(), "下载任务应该存在");
        assertEquals(task.getRemoteFilePath(), loaded.get().getRemoteFilePath());
        assertEquals(task.getLocalFilePath(), loaded.get().getLocalFilePath());
    }

    @Test
    @DisplayName("保存后文件应该存在且包含状态前缀")
    void shouldCreateFileAfterSave() throws IOException {
        UploadTask task = createUploadTask("transfer-789");

        uploadMetaStore.saveTask(task);

        Path expectedFile = tempDir.resolve("uploads").resolve("PREPARED-upload-transfer-789.json");
        assertTrue(Files.exists(expectedFile), "元数据文件应该存在");
    }

    @Test
    @DisplayName("加载不存在的任务应该返回空Optional")
    void shouldReturnEmptyForNonExistentTask() {
        Optional<UploadTask> loaded = uploadMetaStore.loadTask("non-existent");

        assertFalse(loaded.isPresent(), "不存在的任务应该返回空");
    }

    @Test
    @DisplayName("删除任务后应该无法加载")
    void shouldDeleteTask() throws IOException {
        UploadTask task = createUploadTask("transfer-to-delete");

        uploadMetaStore.saveTask(task);
        assertTrue(uploadMetaStore.loadTask("transfer-to-delete").isPresent(), "保存后应该存在");

        uploadMetaStore.deleteTask("transfer-to-delete");
        assertFalse(uploadMetaStore.loadTask("transfer-to-delete").isPresent(), "删除后不应该存在");
    }

    @Test
    @DisplayName("删除不存在的任务不应该抛异常")
    void shouldNotThrowWhenDeletingNonExistentTask() {
        assertDoesNotThrow(() ->
            uploadMetaStore.deleteTask("non-existent")
        );
    }

    @Test
    @DisplayName("恢复未完成任务应该返回所有未完成任务")
    void shouldRecoverPendingTasks() throws IOException {
        UploadTask task1 = createUploadTask("pending-1");
        UploadTask task2 = createUploadTask("pending-2");
        task1.setStatus(UploadTaskStatus.UPLOADING_CHUNKS);
        task2.setStatus(UploadTaskStatus.INIT_UPLOAD_COMPLETED);

        uploadMetaStore.saveTask(task1);
        uploadMetaStore.saveTask(task2);

        List<UploadTask> pendingTasks = uploadMetaStore.recoverPendingTasks();

        assertEquals(2, pendingTasks.size(), "应该恢复2个未完成任务");
    }

    @Test
    @DisplayName("已完成的任务不应该被恢复为待处理")
    void shouldNotRecoverCompletedTasks() throws IOException {
        UploadTask completedTask = createUploadTask("completed-1");
        completedTask.setStatus(UploadTaskStatus.UPLOAD_SUCCESS);

        uploadMetaStore.saveTask(completedTask);

        List<UploadTask> pendingTasks = uploadMetaStore.recoverPendingTasks();

        assertTrue(pendingTasks.isEmpty(), "已完成的任务不应该出现在待处理列表中");
    }

    @Test
    @DisplayName("清理过期任务应该移除超时任务")
    void shouldCleanupExpiredTasks() throws IOException, InterruptedException {
        UploadTask expiredTask = createUploadTask("expired-1");
        expiredTask.setStatus(UploadTaskStatus.UPLOADING_CHUNKS);
        expiredTask.setUpdateTime(getOldTimestamp());

        uploadMetaStore.saveTask(expiredTask);

        Thread.sleep(100); // 确保时间差
        uploadMetaStore.cleanupExpiredTasks(0); // 立即过期

        assertFalse(uploadMetaStore.loadTask("expired-1").isPresent(), "过期任务应该被清理");
    }

    @Test
    @DisplayName("清理过期任务不应该移除未过期任务")
    void shouldNotCleanupNonExpiredTasks() throws IOException {
        UploadTask activeTask = createUploadTask("active-1");
        activeTask.setStatus(UploadTaskStatus.UPLOADING_CHUNKS);
        activeTask.updateTimestamp(); // 刚刚更新

        uploadMetaStore.saveTask(activeTask);

        uploadMetaStore.cleanupExpiredTasks(Long.MAX_VALUE); // 超时时间设为最大

        assertTrue(uploadMetaStore.loadTask("active-1").isPresent(), "未过期任务不应该被清理");
    }

    @Test
    @DisplayName("处理损坏的JSON文件时不应该抛异常")
    void shouldHandleCorruptedJsonFiles() throws IOException {
        Path corruptedFile = tempDir.resolve("uploads").resolve("CORRUPTED-upload-corrupted.json");
        Files.writeString(corruptedFile, "{invalid json content}");

        List<UploadTask> pendingTasks = uploadMetaStore.recoverPendingTasks();

        assertNotNull(pendingTasks, "即使有损坏文件也不应该返回null");
    }

    @Test
    @DisplayName("覆盖保存应该更新任务内容并清理旧状态文件")
    void shouldOverwriteExistingTask() throws IOException {
        UploadTask originalTask = createUploadTask("overwrite-test");
        originalTask.setStatus(UploadTaskStatus.SCANNED);
        uploadMetaStore.saveTask(originalTask);

        Path oldFile = tempDir.resolve("uploads").resolve("SCANNED-upload-overwrite-test.json");
        assertTrue(Files.exists(oldFile), "旧状态文件应该存在");

        UploadTask updatedTask = createUploadTask("overwrite-test");
        updatedTask.setStatus(UploadTaskStatus.INIT_UPLOADING);
        uploadMetaStore.saveTask(updatedTask);

        Path newFile = tempDir.resolve("uploads").resolve("INIT_UPLOADING-upload-overwrite-test.json");
        assertTrue(Files.exists(newFile), "新状态文件应该存在");
        assertFalse(Files.exists(oldFile), "旧状态文件应该被删除");

        Optional<UploadTask> loaded = uploadMetaStore.loadTask("overwrite-test");
        assertTrue(loaded.isPresent());
        assertEquals(UploadTaskStatus.INIT_UPLOADING, loaded.get().getStatus(), "应该是更新后的状态");
    }

    @Test
    @DisplayName("状态切换时应该只保留当前状态的文件")
    void shouldOnlyKeepCurrentStatusFile() throws IOException {
        UploadTask task = createUploadTask("status-transition");

        task.setStatus(UploadTaskStatus.PREPARED);
        uploadMetaStore.saveTask(task);
        assertEquals(countFilesForTransferId("status-transition"), 1, "PREPARED状态下应该只有1个文件");

        task.setStatus(UploadTaskStatus.SCANNED);
        uploadMetaStore.saveTask(task);
        assertEquals(countFilesForTransferId("status-transition"), 1, "SCANNED状态下应该只有1个文件");

        task.setStatus(UploadTaskStatus.UPLOADING_CHUNKS);
        uploadMetaStore.saveTask(task);
        assertEquals(countFilesForTransferId("status-transition"), 1, "UPLOADING_CHUNKS状态下应该只有1个文件");

        task.setStatus(UploadTaskStatus.UPLOAD_SUCCESS);
        uploadMetaStore.saveTask(task);
        assertEquals(countFilesForTransferId("status-transition"), 1, "UPLOAD_SUCCESS状态下应该只有1个文件");

        Path finalFile = tempDir.resolve("uploads").resolve("UPLOAD_SUCCESS-upload-status-transition.json");
        assertTrue(Files.exists(finalFile), "最终应该是UPLOAD_SUCCESS状态文件");
    }

    @Test
    @DisplayName("删除任务应该清理所有状态文件")
    void shouldDeleteAllStatusFiles() throws IOException {
        UploadTask task = createUploadTask("delete-all-status");
        task.setStatus(UploadTaskStatus.SCANNED);
        uploadMetaStore.saveTask(task);

        assertTrue(countFilesForTransferId("delete-all-status") > 0, "保存后应该有文件");

        uploadMetaStore.deleteTask("delete-all-status");

        assertEquals(countFilesForTransferId("delete-all-status"), 0, "删除后不应该有任何状态文件");
    }

    @Test
    @DisplayName("下载任务也应该支持状态前缀命名")
    void downloadTaskShouldSupportStatusPrefixNaming() throws IOException {
        DownloadTask task = createDownloadTask("download-with-status");
        task.setStatus(DownloadTaskStatus.SCANNED);
        downloadMetaStore.saveTask(task);

        Path expectedFile = tempDir.resolve("downloads").resolve("SCANNED-download-download-with-status.json");
        assertTrue(Files.exists(expectedFile), "下载任务元数据文件应该包含状态前缀");

        Optional<DownloadTask> loaded = downloadMetaStore.loadTask("download-with-status");
        assertTrue(loaded.isPresent());
        assertEquals(DownloadTaskStatus.SCANNED, loaded.get().getStatus());
    }

    @Test
    @DisplayName("JSON文件应该包含所有字段包括null值字段")
    void shouldIncludeAllFieldsIncludingNullOnes() throws IOException {
        UploadTask task = createUploadTask("full-fields-test");

        uploadMetaStore.saveTask(task);

        Path jsonFile = tempDir.resolve("uploads").resolve("PREPARED-upload-full-fields-test.json");
        assertTrue(Files.exists(jsonFile), "JSON文件应该存在");

        String jsonContent = Files.readString(jsonFile);

        assertNotNull(jsonContent, "JSON内容不应该为null");

        assertTrue(jsonContent.contains("\"localFilePath\""), "应该包含localFilePath字段");
        assertTrue(jsonContent.contains("\"remoteTargetPath\""), "应该包含remoteTargetPath字段");
        assertTrue(jsonContent.contains("\"transferId\""), "应该包含transferId字段");
        assertTrue(jsonContent.contains("\"traceId\""), "应该包含traceId字段");
        assertTrue(jsonContent.contains("\"status\""), "应该包含status字段");
        assertTrue(jsonContent.contains("\"listenerClassName\""), "应该包含listenerClassName字段");
        assertTrue(jsonContent.contains("\"remoteAgentApiUrl\""), "应该包含remoteAgentApiUrl字段");
        assertTrue(jsonContent.contains("\"remoteAgentUsername\""), "应该包含remoteAgentUsername字段");
        assertTrue(jsonContent.contains("\"createTime\""), "应该包含createTime字段");
        assertTrue(jsonContent.contains("\"updateTime\""), "应该包含updateTime字段");
        assertTrue(jsonContent.contains("\"enqueuedTime\""), "应该包含enqueuedTime字段");
        assertTrue(jsonContent.contains("\"scannedStartTime\""), "应该包含scannedStartTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"scannedEndTime\""), "应该包含scannedEndTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"initUploadStartTime\""), "应该包含initUploadStartTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"initUploadEndTime\""), "应该包含initUploadEndTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"uploadChunksStartTime\""), "应该包含uploadChunksStartTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"uploadChunksEndTime\""), "应该包含uploadChunksEndTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"mergeChunksStartTime\""), "应该包含mergeChunksStartTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"mergeChunksEndTime\""), "应该包含mergeChunksEndTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"uploadSuccessTime\""), "应该包含uploadSuccessTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"verifyStartTime\""), "应该包含verifyStartTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"verifyEndTime\""), "应该包含verifyEndTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"chunkSize\""), "应该包含chunkSize字段");
        assertTrue(jsonContent.contains("\"totalChunks\""), "应该包含totalChunks字段");
        assertTrue(jsonContent.contains("\"totalSize\""), "应该包含totalSize字段");
        assertTrue(jsonContent.contains("\"exceptionDesc\""), "应该包含exceptionDesc字段（即使为null）");
        assertTrue(jsonContent.contains("\"missingChunks\""), "应该包含missingChunks字段");
        assertTrue(jsonContent.contains("\"uploadChunksCount\""), "应该包含uploadChunksCount字段");
        assertTrue(jsonContent.contains("\"retryCount\""), "应该包含retryCount字段");
    }

    @Test
    @DisplayName("下载任务JSON也应该包含所有字段包括null值字段")
    void shouldIncludeAllDownloadTaskFieldsIncludingNullOnes() throws IOException {
        DownloadTask task = createDownloadTask("download-full-fields");

        downloadMetaStore.saveTask(task);

        Path jsonFile = tempDir.resolve("downloads").resolve("PREPARED-download-download-full-fields.json");
        assertTrue(Files.exists(jsonFile), "下载任务JSON文件应该存在");

        String jsonContent = Files.readString(jsonFile);
        assertNotNull(jsonContent, "JSON内容不应该为null");

        assertTrue(jsonContent.contains("\"transferId\""), "应该包含transferId字段");
        assertTrue(jsonContent.contains("\"traceId\""), "应该包含traceId字段");
        assertTrue(jsonContent.contains("\"remoteFilePath\""), "应该包含remoteFilePath字段");
        assertTrue(jsonContent.contains("\"localFilePath\""), "应该包含localFilePath字段");
        assertTrue(jsonContent.contains("\"tmpLocalFilePath\""), "应该包含tmpLocalFilePath字段（即使为null）");
        assertTrue(jsonContent.contains("\"remoteAgentApiUrl\""), "应该包含remoteAgentApiUrl字段");
        assertTrue(jsonContent.contains("\"remoteAgentUsername\""), "应该包含remoteAgentUsername字段");
        assertTrue(jsonContent.contains("\"totalSize\""), "应该包含totalSize字段");
        assertTrue(jsonContent.contains("\"status\""), "应该包含status字段");
        assertTrue(jsonContent.contains("\"createTime\""), "应该包含createTime字段");
        assertTrue(jsonContent.contains("\"updateTime\""), "应该包含updateTime字段");
        assertTrue(jsonContent.contains("\"enqueuedTime\""), "应该包含enqueuedTime字段");
        assertTrue(jsonContent.contains("\"scannedStartTime\""), "应该包含scannedStartTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"scannedEndTime\""), "应该包含scannedEndTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"initDownloadStartTime\""), "应该包含initDownloadStartTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"initDownloadEndTime\""), "应该包含initDownloadEndTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"downloadChunksStartTime\""), "应该包含downloadChunksStartTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"downloadChunksEndTime\""), "应该包含downloadChunksEndTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"mergeChunksStartTime\""), "应该包含mergeChunksStartTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"mergeChunksEndTime\""), "应该包含mergeChunksEndTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"verifyStartTime\""), "应该包含verifyStartTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"verifyEndTime\""), "应该包含verifyEndTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"downloadSuccessTime\""), "应该包含downloadSuccessTime字段（即使为null）");
        assertTrue(jsonContent.contains("\"chunkSize\""), "应该包含chunkSize字段");
        assertTrue(jsonContent.contains("\"totalChunks\""), "应该包含totalChunks字段");
        assertTrue(jsonContent.contains("\"downloadedChunksCount\""), "应该包含downloadedChunksCount字段");
        assertTrue(jsonContent.contains("\"retryCount\""), "应该包含retryCount字段");
        assertTrue(jsonContent.contains("\"listenerClassName\""), "应该包含listenerClassName字段（即使为null）");
        assertTrue(jsonContent.contains("\"exceptionDesc\""), "应该包含exceptionDesc字段（即使为null）");
    }

    private UploadTask createUploadTask(String transferId) {
        UploadTask task = new UploadTask(
                "/local/path/file.zip",
                "/remote/path/file.zip",
                1048576L,
                "http://192.168.1.100:8080/",
                "root"
        );
        task.setTransferId(transferId);
        return task;
    }

    private DownloadTask createDownloadTask(String transferId) {
        DownloadTask task = new DownloadTask(
                "/remote/source.zip",
                "/local/target.zip",
                5242880L,
                "http://192.168.1.100:8080/",
                "root"
        );
        task.setTransferId(transferId);
        return task;
    }

    private String getOldTimestamp() {
        return "2020-01-01 00:00:00.000";
    }

    private int countFilesForTransferId(String transferId) throws IOException {
        Path uploadDir = tempDir.resolve("uploads");
        if (!Files.exists(uploadDir)) {
            return 0;
        }

        try (var paths = Files.list(uploadDir)) {
            return (int) paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains("upload-" + transferId + ".json"))
                    .count();
        }
    }
}
