package com.cq.agent.client.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Example usage of the AgentUploader client.
 * No local JSON persistence; tasks are in-memory only.
 */
public class UploaderExample {

    private static final Logger logger = LoggerFactory.getLogger(UploaderExample.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        String agentUrl = "http://localhost:7777";
        int concurrentUploads = 4;

        AgentUploader uploader = new AgentUploader(agentUrl, concurrentUploads);
        uploader.init();

        File testFile = createDummyFile("dummy-file.tmp", 20 * 1024 * 1024); // 20 MB
        logger.info("Test file created: {} ({} bytes)", testFile.getAbsolutePath(), testFile.length());

        String remotePath = "./uploaded-files/" + testFile.getName();
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
        } finally {
            uploader.shutdown();
            testFile.delete();
        }
    }

    private static File createDummyFile(String fileName, long size) throws IOException {
        File file = new File(fileName);
        file.deleteOnExit();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(size);
        }
        return file;
    }
}
