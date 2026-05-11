package com.cq.agent.client.upload;

import com.cq.agent.config.AgentConfig;
import com.cq.agent.model.UploadSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author cq 2026/3/23 16:32
 * @since 1.0.0
 */
class PersistentMapTest {

    private static final Logger log = LoggerFactory.getLogger(PersistentMapTest.class);

    private PersistentMap<String, UploadSession> uploadSessionMap;
    private Path testTempDir;

    @BeforeEach
    void setUp() throws Exception {
        AgentConfig agentConfig = new AgentConfig();
        String prefix = "target/";
        Path uploadSessionDbPath = Path.of(prefix + agentConfig.getFileBaseDirectory()).toAbsolutePath()
                .normalize().resolve(agentConfig.getUploadSessionsDbPath() + UUID.randomUUID().toString().replace("-", ""));
        log.info("uploadSessionDbPath: {}", uploadSessionDbPath);
        uploadSessionMap = new PersistentMap<>(uploadSessionDbPath.toString(), "upload-sessions", String.class, UploadSession.class);

        testTempDir = Path.of(prefix + "test-temp-" + UUID.randomUUID().toString().replace("-", ""));
        Files.createDirectories(testTempDir);
        log.info("testTempDir: {}", testTempDir);

        for (int i = 0; i < 10; i++) {
            UploadSession session = createRandomUploadSession(i);
            uploadSessionMap.put(session.getTransferId(), session);
            log.info("Created session {}: {}", i, session.getTransferId());
        }
    }

    private UploadSession createRandomUploadSession(int index) {
        String transferId = "transfer-" + UUID.randomUUID().toString().replace("-", "");
        String traceId = "trace-" + UUID.randomUUID().toString().replace("-", "");
        String destFileDir = "/uploads/test-" + index;
        String destFileName = "test-file-" + index + ".dat";
        long totalSize = 1024 * 1024 + index * 100000L;
        int totalChunks = (int) Math.ceil((double) totalSize / (4 * 1024 * 1024));
        int chunkSize = 4 * 1024 * 1024;
        String tempDirectory = testTempDir.resolve("session-" + transferId).toString();
        long createTime = System.currentTimeMillis() - (long) (Math.random() * 86400000);
        long lastAccessTime = createTime + (long) (Math.random() * 3600000);
        
        Set<Integer> receivedChunks = ConcurrentHashMap.newKeySet();
        int receivedCount = (int) (Math.random() * totalChunks);
        for (int i = 0; i < receivedCount; i++) {
            int chunkIndex = (int) (Math.random() * totalChunks);
            receivedChunks.add(chunkIndex);
        }
        
        boolean completed = receivedCount == totalChunks || Math.random() > 0.7;
        boolean merged = completed && Math.random() > 0.5;

        return new UploadSession(
                transferId,
                traceId,
                destFileDir,
                destFileName,
                totalSize,
                totalChunks,
                chunkSize,
                tempDirectory,
                createTime,
                lastAccessTime,
                receivedChunks,
                completed,
                merged
        );
    }

    @AfterEach
    void tearDown() {
        if (uploadSessionMap != null) {
            try {
                uploadSessionMap.close();
            } catch (Exception e) {
                System.err.println("Error closing taskInflightMap: " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("测试PersistentMap的getAllValues方法是否能正确返回所有值 - 用法: 打印当前所有上传中或已完成的任务")
    public void testPrintAllInflightTasks() {
        List<UploadSession> allSessions = uploadSessionMap.getAllValues();
        allSessions.forEach(task -> log.info(task.toString()));
    }

}