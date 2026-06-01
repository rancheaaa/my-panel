package com.cq.agent.client.upload;

import com.cq.agent.client.TransferMetaStore;
import com.cq.agent.client.download.DownloadTask;
import com.cq.agent.client.download.DownloadTaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

        Thread.sleep(100);
        uploadMetaStore.cleanupExpiredTasks(0);

        assertFalse(uploadMetaStore.loadTask("expired-1").isPresent(), "过期任务应该被清理");
    }

    @Test
    @DisplayName("清理过期任务不应该移除未过期任务")
    void shouldNotCleanupNonExpiredTasks() throws IOException {
        UploadTask activeTask = createUploadTask("active-1");
        activeTask.setStatus(UploadTaskStatus.UPLOADING_CHUNKS);
        activeTask.updateTimestamp();

        uploadMetaStore.saveTask(activeTask);

        uploadMetaStore.cleanupExpiredTasks(Long.MAX_VALUE);

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

    // ==================== 内存Map相关测试 ====================

    @Nested
    @DisplayName("内存Map - 启动加载与一致性")
    class MemoryMapLoadAndConsistency {

        @Test
        @DisplayName("构造函数应从磁盘加载已有任务到内存")
        void shouldLoadExistingTasksIntoMemoryOnStartup() throws Exception {
            UploadTask task = createUploadTask("startup-load");
            uploadMetaStore.saveTask(task);

            assertEquals(1, uploadMetaStore.getMemorySize());

            Path sameUploadDir = tempDir.resolve("uploads");
            TransferMetaStore<UploadTask> freshStore = new TransferMetaStore<>(sameUploadDir, UploadTask.class);
            assertEquals(1, freshStore.getMemorySize(), "新store从磁盘加载后内存中应有1个任务");
            assertTrue(freshStore.loadTask("startup-load").isPresent());
        }

        @Test
        @DisplayName("启动时加载多个任务到内存")
        void shouldLoadMultipleTasksIntoMemoryOnStartup() throws Exception {
            for (int i = 0; i < 5; i++) {
                UploadTask t = createUploadTask("multi-" + i);
                uploadMetaStore.saveTask(t);
            }
            assertEquals(5, uploadMetaStore.getMemorySize());

            Path sameUploadDir = tempDir.resolve("uploads");
            TransferMetaStore<UploadTask> freshStore = new TransferMetaStore<>(sameUploadDir, UploadTask.class);
            assertEquals(5, freshStore.getMemorySize());
        }

        @Test
        @DisplayName("磁盘损坏的JSON文件不应阻止其他文件加载")
        void shouldSkipCorruptedJsonFilesOnLoad() throws Exception {
            UploadTask goodTask = createUploadTask("good-task");
            uploadMetaStore.saveTask(goodTask);

            Path corruptedPath = tempDir.resolve("uploads").resolve("CORRUPTED-upload-bad.json");
            Files.writeString(corruptedPath, "{this is not valid json!!!}");

            Path sameUploadDir = tempDir.resolve("uploads");
            TransferMetaStore<UploadTask> freshStore = new TransferMetaStore<>(sameUploadDir, UploadTask.class);
            assertEquals(1, freshStore.getMemorySize(), "只应加载有效的JSON文件");
        }

        @Test
        @DisplayName("空目录启动时内存Map为空")
        void shouldHaveEmptyMemoryMapWhenEmptyDirectory() {
            assertEquals(0, uploadMetaStore.getMemorySize());
            assertEquals(0, uploadMetaStore.getLocalPathIndexSize());
        }
    }

    @Nested
    @DisplayName("内存Map - 写操作同步")
    class MemoryMapWriteSync {

        @Test
        @DisplayName("saveTask后内存Map大小增加")
        void memorySizeShouldIncreaseAfterSave() throws Exception {
            assertEquals(0, uploadMetaStore.getMemorySize());
            uploadMetaStore.saveTask(createUploadTask("sync-save"));
            assertEquals(1, uploadMetaStore.getMemorySize());
        }

        @Test
        @DisplayName("deleteTask后内存Map和索引同步清除")
        void memoryShouldBeClearedAfterDelete() throws Exception {
            uploadMetaStore.saveTask(createUploadTask("sync-del"));
            assertEquals(1, uploadMetaStore.getMemorySize());

            uploadMetaStore.deleteTask("sync-del");
            assertEquals(0, uploadMetaStore.getMemorySize());
        }

        @Test
        @DisplayName("覆盖保存更新内存中的task对象")
        void memoryShouldUpdateOnOverwriteSave() throws Exception {
            UploadTask task1 = createUploadTask("overwrite-mem");
            task1.setStatus(UploadTaskStatus.PREPARED);
            uploadMetaStore.saveTask(task1);

            UploadTask task2 = createUploadTask("overwrite-mem");
            task2.setStatus(UploadTaskStatus.UPLOADING_CHUNKS);
            uploadMetaStore.saveTask(task2);

            Optional<UploadTask> loaded = uploadMetaStore.loadTask("overwrite-mem");
            assertTrue(loaded.isPresent());
            assertEquals(UploadTaskStatus.UPLOADING_CHUNKS, loaded.get().getStatus(),
                    "覆盖保存后内存中应为最新状态");
            assertEquals(1, uploadMetaStore.getMemorySize(), "覆盖保存不应增加内存数量");
        }

        @Test
        @DisplayName("cleanupExpiredTasks清除过期任务的内存记录")
        void memoryShouldBeCleanedAfterCleanupExpired() throws Exception {
            UploadTask expired = createUploadTask("mem-expired");
            expired.setStatus(UploadTaskStatus.UPLOADING_CHUNKS);
            expired.setUpdateTime(getOldTimestamp());
            uploadMetaStore.saveTask(expired);

            assertEquals(1, uploadMetaStore.getMemorySize());
            uploadMetaStore.cleanupExpiredTasks(0);
            assertEquals(0, uploadMetaStore.getMemorySize(), "清理后内存应为空");
        }
    }

    @Nested
    @DisplayName("内存Map - 查询性能验证")
    class MemoryMapQueryPerformance {

        @Test
        @DisplayName("loadTask通过内存Map快速查找")
        void loadTaskShouldUseMemoryLookup() throws Exception {
            int count = 30;
            for (int i = 0; i < count; i++) {
                UploadTask t = createUploadTask("perf-load-" + i);
                uploadMetaStore.saveTask(t);
            }
            assertEquals(count, uploadMetaStore.getMemorySize());
            assertTrue(uploadMetaStore.loadTask("perf-load-15").isPresent());
            assertFalse(uploadMetaStore.loadTask("perf-load-99").isPresent());
        }

        @Test
        @DisplayName("existsTaskWithLocalPath通过内存索引快速判断")
        void existsTaskWithLocalPathShouldUseMemoryIndex() throws Exception {
            UploadTask task = createUploadTask("localpath-test");
            uploadMetaStore.saveTask(task);

            String localPath = "/local/path/file.zip";
            String targetAgent = "192.168.1.100:8080";

            assertTrue(uploadMetaStore.existsTaskWithLocalPath(localPath, targetAgent),
                    "已保存的任务应通过本地路径找到");
            assertFalse(uploadMetaStore.existsTaskWithLocalPath("/non/exist/path", targetAgent));
            assertFalse(uploadMetaStore.existsTaskWithLocalPath(localPath, "999.999.999:9999"));
        }

        @Test
        @DisplayName("existsTaskWithLocalPath对null参数返回false")
        void existsTaskShouldReturnFalseForNullParams() {
            assertFalse(uploadMetaStore.existsTaskWithLocalPath(null, "agent"));
            assertFalse(uploadMetaStore.existsTaskWithLocalPath("/path", null));
            assertFalse(uploadMetaStore.existsTaskWithLocalPath(null, null));
        }

        @Test
        @DisplayName("recoverPendingTasks通过内存Map过滤")
        void recoverPendingTasksShouldUseMemoryFilter() throws Exception {
            UploadTask pending1 = createUploadTask("mem-pend-1");
            pending1.setStatus(UploadTaskStatus.UPLOADING_CHUNKS);
            UploadTask pending2 = createUploadTask("mem-pend-2");
            pending2.setStatus(UploadTaskStatus.INIT_UPLOAD_COMPLETED);
            UploadTask completed = createUploadTask("mem-done-1");
            completed.setStatus(UploadTaskStatus.UPLOAD_SUCCESS);

            uploadMetaStore.saveTask(pending1);
            uploadMetaStore.saveTask(pending2);
            uploadMetaStore.saveTask(completed);

            List<UploadTask> recovered = uploadMetaStore.recoverPendingTasks();
            assertEquals(2, recovered.size(), "应只返回未完成的任务");
            assertTrue(recovered.stream().allMatch(t ->
                    !t.getStatus().toString().contains("SUCCESS")));
        }

        @Test
        @DisplayName("delete后existsTaskWithLocalPath返回false")
        void existsTaskShouldReturnFalseAfterDelete() throws Exception {
            UploadTask task = createUploadTask("del-exists");
            uploadMetaStore.saveTask(task);

            assertTrue(uploadMetaStore.existsTaskWithLocalPath("/local/path/file.zip", "192.168.1.100:8080"));

            uploadMetaStore.deleteTask("del-exists");
            assertFalse(uploadMetaStore.existsTaskWithLocalPath("/local/path/file.zip", "192.168.1.100:8080"));
        }
    }

    @Nested
    @DisplayName("内存Map - 边界条件")
    class MemoryMapEdgeCases {

        @Test
        @DisplayName("大量任务写入后内存大小准确")
        void memorySizeAccurateAfterManyWrites() throws Exception {
            int count = 20;
            for (int i = 0; i < count; i++) {
                UploadTask t = createUploadTask("bulk-mem-" + i);
                uploadMetaStore.saveTask(t);
            }
            assertEquals(count, uploadMetaStore.getMemorySize());
        }

        @Test
        @DisplayName("删除不存在的transferId不抛异常且内存不变")
        void deleteNonExistentShouldNotThrowOrChangeMemory() throws Exception {
            assertDoesNotThrow(() -> uploadMetaStore.deleteTask("nonexistent-99999"));
            assertEquals(0, uploadMetaStore.getMemorySize());
        }

        @Test
        @DisplayName("recoverPendingTasks在空内存上返回空列表")
        void recoverEmptyMemoryReturnsEmptyList() {
            List<UploadTask> result = uploadMetaStore.recoverPendingTasks();
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("loadTask对null transferId返回empty")
        void loadTaskNullReturnsEmpty() {
            Optional<UploadTask> result = uploadMetaStore.loadTask(null);
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("多次save不同transferId的任务，每个都能独立查询和删除")
        void multipleIndependentTasksCanBeManaged() throws Exception {
            UploadTask t1 = createUploadTask("ind-1");
            UploadTask t2 = createUploadTask("ind-2");
            UploadTask t3 = createUploadTask("ind-3");

            uploadMetaStore.saveTask(t1);
            uploadMetaStore.saveTask(t2);
            uploadMetaStore.saveTask(t3);

            assertEquals(3, uploadMetaStore.getMemorySize());

            uploadMetaStore.deleteTask("ind-2");
            assertEquals(2, uploadMetaStore.getMemorySize());
            assertTrue(uploadMetaStore.loadTask("ind-1").isPresent());
            assertFalse(uploadMetaStore.loadTask("ind-2").isPresent());
            assertTrue(uploadMetaStore.loadTask("ind-3").isPresent());
        }
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
