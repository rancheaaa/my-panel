package com.cq.agent.client.upload;

import com.cq.agent.client.BaseIntegrationTest;
import com.cq.agent.client.RemoteAgentInfo;
import com.cq.agent.config.AgentConfig;
import org.junit.jupiter.api.*;
import com.google.gson.JsonObject;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.*;
import java.net.URLEncoder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author cq 2026/3/18 15:01
 * @since 1.0.0
 */
class AgentUploaderIntegrationTest extends BaseIntegrationTest {

    private AgentUploader uploader;

    @BeforeEach
    void setUp() {
        AgentConfig config = new AgentConfig();
        config.setAgentApiUrl(AGENT_URL);
        this.uploader = new AgentUploader(config);
        this.uploader.init();
    }

    @AfterEach
    void tearDown() {
        this.uploader.shutdown();
    }

    @Test
    @DisplayName("测试文件上传集成 - 用法: 测试分块上传文件到agent服务")
    public void testUpload() throws IOException {
        final int random = ThreadLocalRandom.current().nextInt(2, 6);
        File testFile = createDummyFile(random * 10 * 1024 * 1024L); // 20-50 MB
        // Standard remote path, relative to agent's base directory
        // Standard remote path, must be absolute
//        String remotePath = "test/" + testFile.getName();
        String remotePath = AGENT_URL + "@cq:" +  "/tmp/uploaded-files/" + testFile.getName();
        CountDownLatch latch = new CountDownLatch(1);

        UploadListener listener = new UploadListener() {
            @Override
            public void onProgress(int totalChunks, int uploadedChunks, double progress) {
                logger.info("Upload progress: {}% (chunk {}/{})", String.format("%.2f", progress), uploadedChunks, totalChunks);
            }

            @Override
            public void onComplete(UploadTask result) {
                logger.info("Upload complete: path={}, size={}, state={}",
                        result.getLocalFilePath(), result.getTotalSize(), result.getStatus());
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                logger.error("Upload failed: {}", errorMessage);
                latch.countDown();
            }
        };

        try {
            uploader.getAllInflightTasks().forEach(task -> logger.info("Inflight task: {}", task));

            boolean success = uploader.uploadFile(testFile.getAbsolutePath(), remotePath, listener);
            assertTrue(success, "Upload failed");

            if (!latch.await(5, TimeUnit.MINUTES)) {
                logger.error("Upload timed out after 5 minutes");
            }
            
            // Verify file exists on agent
            RemoteAgentInfo remoteAgentInfo = Util.resolveRemoteAgentInfo(remotePath);
            boolean fileExists = verifyFileExists(remoteAgentInfo.getDestFilePath());
            assertTrue(fileExists, "Uploaded file does not exist on agent: " + remoteAgentInfo.getDestFilePath());
            logger.info("File verification successful: {}", remoteAgentInfo.getDestFilePath());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            final boolean count = testFile.delete();
            logger.debug("test file delete {} count", count);
        }
    }

    @Test
    @DisplayName("测试文件上传集成 - 用法: 测试清除所有上传中的任务")
    public void testClearAllInflightTasks() {
        logger.info("agent uploader inflight task is empty {}", uploader.isInflightTasksEmpty());
        uploader.getAllInflightTasks().forEach(task -> logger.info("Inflight task: {}", task));
        uploader.clearAllInflightTasks();
        logger.info("agent uploader inflight task is empty {}", uploader.isInflightTasksEmpty());
    }

    @Test
    @DisplayName("测试文件上传集成 - 用法: 打印当前所有上传中或已完成的任务")
    public void testPrintAllInflightTasks() {
        logger.info("agent uploader inflight task is empty: {}", uploader.isInflightTasksEmpty());
        uploader.getAllInflightTasks().forEach(task -> logger.info("Current Inflight task: {}", task));
    }

    private File createDummyFile(long size) throws IOException {
        final File file = File.createTempFile("my-panel.", ".dat");
        file.deleteOnExit();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(size);
        }
        return file;
    }

    /**
     * 验证文件是否存在
     * @param remotePath 远程文件路径
     * @return 是否存在
     * @throws IOException  IOException
     */
    protected boolean verifyFileExists(String remotePath) throws IOException {
        String url = AGENT_URL + "/api/file/exists?path=" + URLEncoder.encode(remotePath, "UTF-8");
        logger.info("Verifying file existence path: {}", url);

        final JsonObject res = sendGetRequest(url);
        logger.info("Verify file existence response: {}", res.toString());
        Assertions.assertTrue(res.has("data"), "Response should contain data field");
        return res.get("data").getAsBoolean();
    }
}