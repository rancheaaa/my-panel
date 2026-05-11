package com.cq.agent.client.download;

import com.cq.agent.config.AgentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentDownloader - 基础重构测试")
class AgentDownloaderTest {

    @Test
    @DisplayName("应该接受 metaDirPath 参数")
    void shouldAcceptMetaDirPath() {
        AgentConfig config = new AgentConfig();
        
        assertDoesNotThrow(() -> {
            AgentDownloader downloader = new AgentDownloader(config, "./test-meta-dir");
            assertNotNull(downloader);
            downloader.shutdown();
        }, "构造函数应该接受 metaDirPath 参数");
    }

    @Test
    @DisplayName("downloadFile 应该验证空参数")
    void downloadFileShouldValidateNullParams() {
        AgentConfig config = new AgentConfig();
        AgentDownloader downloader = new AgentDownloader(config, "./test-meta-dir");

        boolean result = downloader.downloadFile(null, "/local/test.txt", null);
        assertFalse(result, "null remoteFileInfo 应该返回 false");

        result = downloader.downloadFile("", "/local/test.txt", null);
        assertFalse(result, "空 remoteFileInfo 应该返回 false");

        result = downloader.downloadFile("192.168.1.100:7777@root:/tmp/test", null, null);
        assertFalse(result, "null localFilePath 应该返回 false");

        downloader.shutdown();
    }
}
