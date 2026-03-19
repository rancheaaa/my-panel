package com.cq.agent.client.upload;

import com.cq.agent.client.BaseIntegrationTest;
import com.cq.agent.config.AgentConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    @Test
    @DisplayName("测试文件上传集成 - 用法: 测试分块上传文件到agent服务")
    public void testUpload() throws IOException {
        // Change this to your agent's address
        String agentUrl = "http://172.19.200.130:7777";
        int concurrentUploads = 2;

        AgentConfig config = new AgentConfig();
        AgentUploader uploader = new AgentUploader(config, agentUrl, concurrentUploads);
        uploader.init();
        final int random = ThreadLocalRandom.current().nextInt(2, 6);
        File testFile = createDummyFile(random * 10 * 1024 * 1024L); // 20-50 MB
        // Standard remote path, relative to agent's base directory
        // Standard remote path, must be absolute
//        String remotePath = "test/" + testFile.getName();
        String remotePath = "/tmp/uploaded-files/" + testFile.getName();
        CountDownLatch latch = new CountDownLatch(1);

        UploadListener listener = new UploadListener() {
            @Override
            public void onProgress(int totalChunks, int uploadedChunks, double progress) {
                logger.info("Upload progress: {}% (chunk {}/{})", String.format("%.2f", progress), uploadedChunks, totalChunks);
            }

            @Override
            public void onComplete(UploadResult result) {
                logger.info("Upload complete: path={}, size={}, checksum={}, state={}",
                        result.getPath(), result.getSize(), result.getChecksum(), result.getCompletionState());
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                logger.error("Upload failed: {}", errorMessage);
                latch.countDown();
            }
        };

        try {
            uploader.uploadFile(testFile.getAbsolutePath(), remotePath, listener);

            if (!latch.await(5, TimeUnit.MINUTES)) {
                logger.error("Upload timed out after 5 minutes");
            }
            
            // Verify file exists on agent
            boolean fileExists = verifyFileExists(remotePath);
            assertTrue(fileExists, "Uploaded file does not exist on agent: " + remotePath);
            logger.info("File verification successful: {}", remotePath);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            uploader.shutdown();
            final boolean count = testFile.delete();
            logger.debug("test file delete {} count", count);
        }
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