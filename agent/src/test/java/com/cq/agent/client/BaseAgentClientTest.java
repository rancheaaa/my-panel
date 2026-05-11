package com.cq.agent.client;

import com.cq.agent.client.upload.TransferMetaStore;
import com.cq.agent.client.upload.UploadTask;
import com.cq.agent.client.upload.UploadTaskStatus;
import com.cq.agent.config.AgentConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BaseAgentClient - 重构后（无RocksDB）")
class BaseAgentClientTest {

    @TempDir
    Path tempDir;

    private TestableBaseClient client;

    @BeforeEach
    void setUp() throws Exception {
        Path metaDir = tempDir.resolve("meta");
        java.nio.file.Files.createDirectories(metaDir);

        client = new TestableBaseClient(metaDir);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("应该使用内存队列而非 RocksDB")
    void shouldUseMemoryQueueInsteadOfRocksDB() throws Exception {
        Field queueField = BaseAgentClient.class.getDeclaredField("taskQueue");
        queueField.setAccessible(true);
        Object queue = queueField.get(client);

        assertInstanceOf(ConcurrentLinkedQueue.class, queue, "应该是 ConcurrentLinkedQueue 而非 PersistentQueue");
    }

    @Test
    @DisplayName("应该使用内存 Map 而非 RocksDB")
    void shouldUseMemoryMapInsteadOfRocksDB() throws Exception {
        Field mapField = BaseAgentClient.class.getDeclaredField("inflightTasks");
        mapField.setAccessible(true);
        Object map = mapField.get(client);

        assertInstanceOf(ConcurrentMap.class, map, "应该是 ConcurrentHashMap 而非 PersistentMap");
    }

    @Test
    @DisplayName("应该初始化 TransferMetaStore")
    void shouldInitializeTransferMetaStore() throws Exception {
        Field metaStoreField = BaseAgentClient.class.getDeclaredField("metaStore");
        metaStoreField.setAccessible(true);
        Object metaStore = metaStoreField.get(client);

        assertNotNull(metaStore, "TransferMetaStore 应该被初始化");
        assertInstanceOf(TransferMetaStore.class, metaStore);
    }

    @Test
    @DisplayName("添加任务到队列后应该可以消费")
    void shouldConsumeTaskFromQueue() throws Exception {
        UploadTask task = createTestUploadTask("test-queue-1");

        client.enqueueTask(task);

        Thread.sleep(2000); // 等待 worker 处理

        assertTrue(client.isProcessed("test-queue-1") || client.taskQueue.isEmpty(), 
                   "任务应该被处理或队列已清空");
    }

    @Test
    @DisplayName("任务处理过程中状态应该被更新")
    void shouldUpdateTaskStatusDuringProcessing() throws Exception {
        UploadTask task = createTestUploadTask("test-status-1");

        client.enqueueTask(task);

        Thread.sleep(2000); // 等待处理完成（增加到2秒）

        Optional<UploadTask> loaded = client.getMetaStore().loadTask("test-status-1");
        assertTrue(loaded.isPresent());
        assertEquals(UploadTaskStatus.UPLOAD_SUCCESS, loaded.get().getStatus(), "最终状态应该是成功");
    }

    @Test
    @DisplayName("关闭客户端应该清理资源")
    void shouldCleanupResourcesOnShutdown() {
        assertDoesNotThrow(() -> client.shutdown(), "关闭不应该抛异常");
    }

    @Test
    @DisplayName("并发添加多个任务都应该被处理")
    void handleMultipleTasksConcurrently() throws Exception {
        int taskCount = 5;
        CountDownLatch latch = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
            final String transferId = "concurrent-" + i;
            new Thread(() -> {
                try {
                    UploadTask task = createTestUploadTask(transferId);
                    client.enqueueTask(task);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(500); // 等待所有任务处理完毕

        List<UploadTask> pending = client.getMetaStore().recoverPendingTasks();
        assertTrue(pending.isEmpty(), "所有任务应该已完成，不应有待处理任务");
    }

    private UploadTask createTestUploadTask(String transferId) {
        UploadTask task = new UploadTask(
                "/local/test.zip",
                "/remote/test.zip",
                1024L,
                "http://localhost:8080/",
                "test"
        );
        task.setTransferId(transferId);
        return task;
    }

    /**
     * 测试用的 BaseAgentClient 实现
     */
    static class TestableBaseClient extends BaseAgentClient<UploadTask, Object> {

        private final TransferMetaStore<UploadTask> metaStore;
        private final ConcurrentLinkedQueue<UploadTask> processedTasks = new ConcurrentLinkedQueue<>();
        private final ConcurrentHashMap<String, Boolean> processedFlags = new ConcurrentHashMap<>();

        public TestableBaseClient(Path metaDir) throws Exception {
            super(createMockConfig(), 2, 100, 1, 3, 1000,
                  10, 30, metaDir.toString(),
                  0, UploadTask.class, "TEST");

            this.metaStore = new TransferMetaStore<>(metaDir, UploadTask.class);
            setMetaStore(this.metaStore);
            init();
        }

        private static AgentConfig createMockConfig() {
            return new AgentConfig(); // 使用默认配置
        }

        @Override
        protected void processTask(UploadTask task) {
            try {
                updateTaskStatus(task, UploadTaskStatus.UPLOADING_CHUNKS);
                Thread.sleep(50); // 模拟处理时间
                updateTaskStatus(task, UploadTaskStatus.UPLOAD_SUCCESS);
                
                processedTasks.add(task);
                processedFlags.put(task.getTransferId(), true);
                
                // 从 inflightTasks 中移除
                inflightTasks.remove(getTaskKey(task));
            } catch (Exception e) {
                logger.error("处理任务失败: {}", task.getTransferId(), e);
            }
        }

        @Override
        protected String getTaskKey(UploadTask task) {
            return task.getTransferId();
        }

        @Override
        protected void onListenerProgress(Object listener, int total, int processed, double progress) {}

        @Override
        protected void onListenerBeforeSend(Object listener, UploadTask task) {}

        @Override
        protected void onListenerSuccess(Object listener, UploadTask result) {}

        @Override
        protected void onListenerError(Object listener, String message) {}

        public void enqueueTask(UploadTask task) throws Exception {
            taskQueue.offer(task);
        }

        public boolean isProcessed(String transferId) {
            return processedFlags.containsKey(transferId);
        }

        public TransferMetaStore<UploadTask> getMetaStore() {
            return metaStore;
        }

        private void setMetaStore(TransferMetaStore<UploadTask> store) throws Exception {
            Field field = BaseAgentClient.class.getDeclaredField("metaStore");
            field.setAccessible(true);
            field.set(this, store);
        }
    }
}
