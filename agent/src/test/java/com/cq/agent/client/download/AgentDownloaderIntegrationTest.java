package com.cq.agent.client.download;

import com.cq.agent.client.BaseIntegrationTest;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.upload.UploadListener;
import com.cq.agent.client.upload.UploadTask;
import com.cq.agent.config.AgentConfig;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.URLEncoder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * AgentDownloader集成测试类
 *
 * @author cq 2026/3/24 14:14
 * @since 1.0.0
 */
class AgentDownloaderIntegrationTest extends BaseIntegrationTest {

    private AgentDownloader downloader;
    private AgentUploader uploader;

    @BeforeEach
    void setUp() {
        int concurrentDownloads = 2;
        int concurrentUploads = 2;

        AgentConfig config = new AgentConfig();
        this.downloader = new AgentDownloader(config, AGENT_URL, concurrentDownloads);
        this.downloader.init();

        this.uploader = new AgentUploader(config, AGENT_URL, concurrentUploads);
        this.uploader.init();
    }

    @AfterEach
    void tearDown() {
        this.downloader.shutdown();
        this.uploader.shutdown();
    }

    @Test
    @DisplayName("测试文件下载集成 - 用法: 测试分块下载文件从agent服务")
    public void testDownload() throws IOException {
        String remotePath = "/tmp/uploaded-files/" + "my-panel.9705081671171337513.dat";
        File localDownloadDir = new File("/tmp/my-panel");
        if (!localDownloadDir.exists()) {
            localDownloadDir.mkdirs();
        }
        File localDownloadFile = new File(localDownloadDir, "my-panel.9705081671171337513.dat");

        CountDownLatch downloadLatch = new CountDownLatch(1);

        DownloadListener downloadListener = new DownloadListener() {
            @Override
            public void onProgress(int total, int downloaded, double progress) {
                logger.info("Download progress: {}% (chunk {}/{})", String.format("%.2f", progress), downloaded, total);
            }

            @Override
            public void onComplete(DownloadTask result) {
                logger.info("Download complete: remote={}, local={}, size={}, state={}",
                        result.getRemoteFilePath(), result.getLocalFilePath(), result.getTotalSize(), result.getStatus());
                downloadLatch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                logger.error("Download failed: {}", errorMessage);
                downloadLatch.countDown();
            }
        };

        try {
            boolean fileExists = verifyFileExists(remotePath);
            assertTrue(fileExists, "Uploaded file does not exist on agent: " + remotePath);
            logger.info("File upload verification successful: {}", remotePath);

            boolean downloadSuccess = downloader.downloadFile(remotePath, localDownloadFile.getAbsolutePath(), downloadListener);
            assertTrue(downloadSuccess, "Download failed");

            if (!downloadLatch.await(5, TimeUnit.MINUTES)) {
                logger.error("Download timed out after 5 minutes");
                fail("Download timed out");
            }
            assertTrue(localDownloadFile.exists(), "Downloaded file does not exist locally: " + localDownloadFile.getAbsolutePath());
            logger.info("File download verification successful:");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
//            if (localDownloadFile.exists()) {
//                localDownloadFile.delete();
//            }
//            cleanupRemoteFile(remotePath);
        }
    }

    @Test
    @DisplayName("测试文件下载集成 - 用法: 测试分块下载文件从agent服务")
    public void testDownload2() throws IOException {
        final int random = ThreadLocalRandom.current().nextInt(2, 6);
        File testFile = createDummyFile(random * 10 * 1024 * 1024L); // 20-50 MB
        String remotePath = "/tmp/download-test/" + testFile.getName();
        File localDownloadDir = new File(System.getProperty("java.io.tmpdir"), "download-test");
        if (!localDownloadDir.exists()) {
            localDownloadDir.mkdirs();
        }
        File localDownloadFile = new File(localDownloadDir, testFile.getName());

        CountDownLatch uploadLatch = new CountDownLatch(1);
        CountDownLatch downloadLatch = new CountDownLatch(1);

        UploadListener uploadListener = new UploadListener() {
            @Override
            public void onProgress(int totalChunks, int uploadedChunks, double progress) {
                logger.info("Upload progress: {}% (chunk {}/{})", String.format("%.2f", progress), uploadedChunks, totalChunks);
            }

            @Override
            public void onComplete(UploadTask result) {
                logger.info("Upload complete: path={}, size={}, state={}",
                        result.getLocalFilePath(), result.getTotalSize(), result.getStatus());
                uploadLatch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                logger.error("Upload failed: {}", errorMessage);
                uploadLatch.countDown();
            }
        };

        DownloadListener downloadListener = new DownloadListener() {
            @Override
            public void onProgress(int total, int downloaded, double progress) {
                logger.info("Download progress: {}% (chunk {}/{})", String.format("%.2f", progress), downloaded, total);
            }

            @Override
            public void onComplete(DownloadTask result) {
                logger.info("Download complete: remote={}, local={}, size={}, state={}",
                        result.getRemoteFilePath(), result.getLocalFilePath(), result.getTotalSize(), result.getStatus());
                downloadLatch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                logger.error("Download failed: {}", errorMessage);
                downloadLatch.countDown();
            }
        };

        try {
            uploader.getAllInflightTasks().forEach(task -> logger.info("Upload Inflight task: {}", task));

            boolean uploadSuccess = uploader.uploadFile(testFile.getAbsolutePath(), remotePath, uploadListener);
            assertTrue(uploadSuccess, "Upload failed");

            if (!uploadLatch.await(5, TimeUnit.MINUTES)) {
                logger.error("Upload timed out after 5 minutes");
                fail("Upload timed out");
            }

            boolean fileExists = verifyFileExists(remotePath);
            assertTrue(fileExists, "Uploaded file does not exist on agent: " + remotePath);
            logger.info("File upload verification successful: {}", remotePath);

            boolean downloadSuccess = downloader.downloadFile(remotePath, localDownloadFile.getAbsolutePath(), downloadListener);
            assertTrue(downloadSuccess, "Download failed");

            if (!downloadLatch.await(5, TimeUnit.MINUTES)) {
                logger.error("Download timed out after 5 minutes");
                fail("Download timed out");
            }

            assertTrue(localDownloadFile.exists(), "Downloaded file does not exist locally: " + localDownloadFile.getAbsolutePath());

            long originalSize = testFile.length();
            long downloadedSize = localDownloadFile.length();
            assertEquals(originalSize, downloadedSize, "Downloaded file size mismatch. Original: " + originalSize + ", Downloaded: " + downloadedSize);

            logger.info("File download verification successful: original size={}, downloaded size={}", originalSize, downloadedSize);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (testFile.exists()) {
                testFile.delete();
            }
            if (localDownloadFile.exists()) {
                localDownloadFile.delete();
            }
            cleanupRemoteFile(remotePath);
        }
    }

    @Test
    @DisplayName("测试文件下载集成 - 用法: 测试断点续传下载")
    public void testResumableDownload() throws IOException {
        final int random = ThreadLocalRandom.current().nextInt(2, 6);
        File testFile = createDummyFile(random * 10 * 1024 * 1024L); // 20-50 MB
        String remotePath = "/tmp/resumable-download-test/" + testFile.getName();
        File localDownloadDir = new File(System.getProperty("java.io.tmpdir"), "resumable-download-test");
        if (!localDownloadDir.exists()) {
            localDownloadDir.mkdirs();
        }
        File localDownloadFile = new File(localDownloadDir, testFile.getName());

        CountDownLatch uploadLatch = new CountDownLatch(1);

        UploadListener uploadListener = new UploadListener() {
            @Override
            public void onProgress(int totalChunks, int uploadedChunks, double progress) {
                logger.info("Upload progress: {}% (chunk {}/{})", String.format("%.2f", progress), uploadedChunks, totalChunks);
            }

            @Override
            public void onComplete(UploadTask result) {
                logger.info("Upload complete: path={}, size={}, state={}",
                        result.getLocalFilePath(), result.getTotalSize(), result.getStatus());
                uploadLatch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                logger.error("Upload failed: {}", errorMessage);
                uploadLatch.countDown();
            }
        };

        try {
            boolean uploadSuccess = uploader.uploadFile(testFile.getAbsolutePath(), remotePath, uploadListener);
            assertTrue(uploadSuccess, "Upload failed");

            if (!uploadLatch.await(5, TimeUnit.MINUTES)) {
                logger.error("Upload timed out after 5 minutes");
                fail("Upload timed out");
            }

            boolean fileExists = verifyFileExists(remotePath);
            assertTrue(fileExists, "Uploaded file does not exist on agent: " + remotePath);

            CountDownLatch downloadLatch1 = new CountDownLatch(1);
            DownloadListener downloadListener1 = new DownloadListener() {
                private boolean shouldFail = true;

                @Override
                public void onProgress(int total, int downloaded, double progress) {
                    logger.info("Download 1 progress: {}% (chunk {}/{})", String.format("%.2f", progress), downloaded, total);
                    if (shouldFail && progress > 30.0) {
                        shouldFail = false;
                        throw new RuntimeException("Simulated download failure for resumable test");
                    }
                }

                @Override
                public void onComplete(DownloadTask result) {
                    logger.info("Download 1 complete: remote={}, local={}, size={}, state={}",
                            result.getRemoteFilePath(), result.getLocalFilePath(), result.getTotalSize(), result.getStatus());
                    downloadLatch1.countDown();
                }

                @Override
                public void onError(String errorMessage) {
                    logger.info("Download 1 failed (expected): {}", errorMessage);
                    downloadLatch1.countDown();
                }
            };

            boolean downloadSuccess1 = downloader.downloadFile(remotePath, localDownloadFile.getAbsolutePath(), downloadListener1);
            assertTrue(downloadSuccess1, "Download 1 failed");

            downloadLatch1.await(2, TimeUnit.MINUTES);

            long partialSize = localDownloadFile.exists() ? localDownloadFile.length() : 0;
            logger.info("Partial download size before resume: {}", partialSize);

            CountDownLatch downloadLatch2 = new CountDownLatch(1);
            DownloadListener downloadListener2 = new DownloadListener() {
                @Override
                public void onProgress(int total, int downloaded, double progress) {
                    logger.info("Download 2 (resumed) progress: {}% (chunk {}/{})", String.format("%.2f", progress), downloaded, total);
                }

                @Override
                public void onComplete(DownloadTask result) {
                    logger.info("Download 2 (resumed) complete: remote={}, local={}, size={}, state={}",
                            result.getRemoteFilePath(), result.getLocalFilePath(), result.getTotalSize(), result.getStatus());
                    downloadLatch2.countDown();
                }

                @Override
                public void onError(String errorMessage) {
                    logger.error("Download 2 (resumed) failed: {}", errorMessage);
                    downloadLatch2.countDown();
                }
            };

            boolean downloadSuccess2 = downloader.downloadFile(remotePath, localDownloadFile.getAbsolutePath(), downloadListener2);
            assertTrue(downloadSuccess2, "Download 2 failed");

            if (!downloadLatch2.await(5, TimeUnit.MINUTES)) {
                logger.error("Resumable download timed out after 5 minutes");
                fail("Resumable download timed out");
            }

            assertTrue(localDownloadFile.exists(), "Downloaded file does not exist locally: " + localDownloadFile.getAbsolutePath());

            long originalSize = testFile.length();
            long downloadedSize = localDownloadFile.length();
            assertEquals(originalSize, downloadedSize, "Resumable download file size mismatch. Original: " + originalSize + ", Downloaded: " + downloadedSize);

            logger.info("Resumable download verification successful: original size={}, downloaded size={}", originalSize, downloadedSize);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (testFile.exists()) {
                testFile.delete();
            }
            if (localDownloadFile.exists()) {
                localDownloadFile.delete();
            }
//            cleanupRemoteFile(remotePath);
        }
    }

    @Test
    @DisplayName("测试文件下载集成 - 用法: 测试清除所有下载中的任务")
    public void testClearAllInflightTasks() {
//        logger.info("agent downloader inflight task is empty: {}", downloader.isInflightTasksEmpty());
//        downloader.getAllInflightTasks().forEach(task -> logger.info("Inflight task: {}", task));
//        downloader.clearAllInflightTasks();
//        logger.info("agent downloader inflight task is empty: {}", downloader.isInflightTasksEmpty());
    }

    @Test
    @DisplayName("测试文件下载集成 - 用法: 打印当前所有下载中或已完成的任务")
    public void testPrintAllInflightTasks() {
//        logger.info("agent downloader inflight task is empty: {}", downloader.isInflightTasksEmpty());
//        downloader.getAllInflightTasks().forEach(task -> logger.info("Current Inflight task: {}", task));
    }

    private File createDummyFile(long size) throws IOException {
        File file = File.createTempFile("my-panel.", ".dat");
        file.deleteOnExit();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(size);
        }
        return file;
    }

    protected boolean verifyFileExists(String remotePath) throws IOException {
        String url = AGENT_URL + "/api/file/exists?path=" + URLEncoder.encode(remotePath, "UTF-8");
        logger.info("Verifying file existence path: {}", url);

        JsonObject res = sendGetRequest(url);
        logger.info("Verify file existence response: {}", res.toString());
        assertTrue(res.has("data"), "Response should contain data field");
        return res.get("data").getAsBoolean();
    }

    protected void cleanupRemoteFile(String remotePath) {
        try {
            String url = AGENT_URL + "/api/file/delete?path=" + URLEncoder.encode(remotePath, "UTF-8");
            logger.info("Cleaning up remote file: {}", url);
            JsonObject res = sendGetRequest(url);
            logger.info("Cleanup response: {}", res.toString());
        } catch (IOException e) {
            logger.warn("Failed to cleanup remote file: {}", remotePath, e);
        }
    }
}