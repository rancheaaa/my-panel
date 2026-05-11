package com.cq.agent.client.upload;

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
    @DisplayName("保存后文件应该存在")
    void shouldCreateFileAfterSave() throws IOException {
        UploadTask task = createUploadTask("transfer-789");

        uploadMetaStore.saveTask(task);

        Path expectedFile = tempDir.resolve("uploads").resolve("upload-transfer-789.json");
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
        Path corruptedFile = tempDir.resolve("uploads").resolve("upload-corrupted.json");
        Files.writeString(corruptedFile, "{invalid json content}");

        List<UploadTask> pendingTasks = uploadMetaStore.recoverPendingTasks();

        assertNotNull(pendingTasks, "即使有损坏文件也不应该返回null");
    }

    @Test
    @DisplayName("覆盖保存应该更新任务内容")
    void shouldOverwriteExistingTask() throws IOException {
        UploadTask originalTask = createUploadTask("overwrite-test");
        originalTask.setStatus(UploadTaskStatus.SCANNED);
        uploadMetaStore.saveTask(originalTask);

        UploadTask updatedTask = createUploadTask("overwrite-test");
        updatedTask.setStatus(UploadTaskStatus.INIT_UPLOADING);
        uploadMetaStore.saveTask(updatedTask);

        Optional<UploadTask> loaded = uploadMetaStore.loadTask("overwrite-test");
        assertTrue(loaded.isPresent());
        assertEquals(UploadTaskStatus.INIT_UPLOADING, loaded.get().getStatus(), "应该是更新后的状态");
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
}
