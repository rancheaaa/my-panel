package com.cq.agent.client.upload;

import com.cq.agent.client.BaseAgentClient;
import com.cq.agent.config.AgentConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentUploader - 重构后（适配新BaseAgentClient）")
class AgentUploaderTest {

    @TempDir
    Path tempDir;

    private AgentConfig config;
    private Path metaDir;
    private AgentUploader uploader;

    @BeforeEach
    void setUp() throws IOException {
        metaDir = tempDir.resolve("uploads-meta");
        Files.createDirectories(metaDir);

        config = new AgentConfig();
        uploader = new TestableAgentUploader(config, metaDir.toString());
        uploader.init();
    }

    @AfterEach
    void tearDown() {
        if (uploader != null) {
            uploader.shutdown();
        }
    }

    @Test
    @DisplayName("应该成功初始化并使用内存队列")
    void shouldInitializeWithMemoryQueue() throws Exception {
        Field queueField = BaseAgentClient.class.getDeclaredField("taskQueue");
        queueField.setAccessible(true);
        Object queue = queueField.get(uploader);

        assertInstanceOf(ConcurrentLinkedQueue.class, queue, "应该是 ConcurrentLinkedQueue");
    }

    @Test
    @DisplayName("应该正确配置 TransferMetaStore")
    void shouldConfigureTransferMetaStore() throws Exception {
        Field metaStoreField = BaseAgentClient.class.getDeclaredField("metaStore");
        metaStoreField.setAccessible(true);
        Object metaStore = metaStoreField.get(uploader);

        assertNotNull(metaStore, "TransferMetaStore 应该被初始化");
        assertInstanceOf(TransferMetaStore.class, metaStore);
    }

    @Test
    @DisplayName("构造函数不应该接受 RocksDB 路径参数")
    void shouldNotAcceptRocksDbPaths() {
        // 验证构造函数签名已变更：不再需要 queueDbPath 和 mapDbPath
        // 新构造函数只接收 metaDirPath
        assertDoesNotThrow(() -> {
            new TestableAgentUploader(config, metaDir.toString());
        }, "新构造函数应该只接受 metaDirPath 参数");
    }

    @Test
    @DisplayName("uploadFile 应该验证本地文件路径")
    void uploadFileShouldValidateLocalFilePath() {
        boolean result = uploader.uploadFile(null, "remote/path", null);

        assertFalse(result, "null 路径应该返回 false");
    }

    @Test
    @DisplayName("uploadFile 应该验证远程路径")
    void uploadFileShouldValidateRemotePath() {
        boolean result = uploader.uploadFile("/local/file.txt", null, null);

        assertFalse(result, "null 远程路径应该返回 false");
    }

    @Test
    @DisplayName("uploadFile 应该拒绝非绝对路径")
    void uploadFileShouldRejectRelativePath() {
        boolean result = uploader.uploadFile("relative/path.txt", "192.168.1.100:8080@root:/tmp/test", null);

        assertFalse(result, "相对路径应该返回 false");
    }

    @Test
    @DisplayName("getInflightTasksCount 应该返回正确的任务数")
    void shouldReturnCorrectInflightTaskCount() {
        int count = uploader.getInflightTasksCount();

        assertEquals(0, count, "初始时应该没有进行中的任务");
    }

    @Test
    @DisplayName("isInflightTasksEmpty 应该在空队列时返回 true")
    void shouldReturnTrueWhenEmpty() {
        assertTrue(uploader.isInflightTasksEmpty(), "空队列应该返回 true");
    }

    @Test
    @DisplayName("getInflightTasks 分页查询应该正常工作")
    void getInflightTasksPaginationShouldWork() {
        List<UploadTask> tasks = uploader.getInflightTasks(1, 10);

        assertNotNull(tasks, "分页查询不应返回 null");
        assertTrue(tasks.isEmpty(), "无任务时应返回空列表");
    }

    @Test
    @DisplayName("getAllInflightTasks 应该返回空列表当无任务时")
    void getAllInflightTasksShouldReturnEmptyList() {
        List<UploadTask> tasks = uploader.getAllInflightTasks();

        assertNotNull(tasks);
        assertTrue(tasks.isEmpty(), "无任务时应返回空列表");
    }

    @Test
    @DisplayName("shutdown 应该正常清理资源")
    void shutdownShouldCleanupResources() {
        assertDoesNotThrow(() -> uploader.shutdown(), "关闭不应抛异常");
    }

    /**
     * 可测试的 AgentUploader 子类，用于单元测试
     */
    private static class TestableAgentUploader extends AgentUploader {

        public TestableAgentUploader(AgentConfig agentConfig, String metaDirPath) {
            super(agentConfig, metaDirPath);
        }
    }
}
