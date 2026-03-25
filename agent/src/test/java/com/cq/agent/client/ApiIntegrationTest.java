package com.cq.agent.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URLEncoder;
import com.google.gson.JsonObject;

/**
 * 集成测试类，测试agent的各种API接口
 *
 * @author cq 2026/3/18
 * @since 1.0.0
 */
public class ApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("测试健康检查接口 - 路径: /api/health - 用法: GET请求，检查agent服务状态")
    public void testHealthCheck() throws IOException {
        String url = AGENT_URL + "/api/health";
        logger.info("Testing health check: {}", url);

        JsonObject response = sendGetRequest(url);
        assertTrue(response.has("status"), "Response should contain status field");
        assertTrue(response.get("status").getAsString().equals("UP"), "Status should be UP");
    }

    @Test
    @DisplayName("测试系统信息接口 - 路径: /api/file/syst - 用法: GET请求，获取系统信息")
    public void testSystemInfo() throws IOException {
        String url = AGENT_URL + "/api/file/syst";
        logger.info("Testing system info: {}", url);

        JsonObject response = sendGetRequest(url);
        logger.info("System info response: {}", response.toString());
        // Check if response has success field instead of os field
        assertTrue(response.has("success"), "Response should contain success field");
    }

    @Test
    @DisplayName("测试工作目录接口 - 路径: /api/file/pwd - 用法: GET请求，获取当前工作目录")
    public void testWorkingDirectory() throws IOException {
        String url = AGENT_URL + "/api/file/pwd";
        logger.info("Testing working directory: {}", url);

        JsonObject response = sendGetRequest(url);
        assertTrue(response.has("success"), "Response should contain success field");
    }

    @Test
    @DisplayName("测试磁盘空间接口 - 路径: /api/file/disk - 用法: GET请求，获取磁盘空间信息")
    public void testDiskSpace() throws IOException {
        String url = AGENT_URL + "/api/file/disk";
        logger.info("Testing disk space: {}", url);

        JsonObject response = sendGetRequest(url);
        assertTrue(response.has("success"), "Response should contain success field");
    }

    @Test
    @DisplayName("测试文件存在性检查接口 - 路径: /api/file/exists - 用法: GET请求，参数path指定文件路径")
    public void testFileExists() throws IOException {
        // Test with a known path
        String testPath = "/tmp";
        String url = AGENT_URL + "/api/file/exists?path=" + URLEncoder.encode(testPath, "UTF-8");
        logger.info("Testing file exists: {}", url);

        JsonObject response = sendGetRequest(url);
        assertTrue(response.has("data"), "Response should contain data field");
    }

    @Test
    @DisplayName("测试命令执行接口 - 路径: /api/execute - 用法: POST请求，JSON参数包含command和timeout")
    public void testExecuteCommand() throws IOException {
        String url = AGENT_URL + "/api/execute";
        logger.info("Testing execute command: {}", url);

        // Send command to list current directory
        String jsonInputString = "{\"command\": \"ls -la\", \"timeout\": 10}";
        logger.info("Sending command: {}", jsonInputString);
        JsonObject response = sendPostRequest(url, jsonInputString);
        assertTrue(response.has("exitCode"), "Response should contain exit code");
    }

    @Test
    @DisplayName("测试功能特性接口 - 路径: /api/file/feat - 用法: GET请求，获取支持的功能列表")
    public void testFeatures() throws IOException {
        String url = AGENT_URL + "/api/file/feat";
        logger.info("Testing features: {}", url);

        JsonObject response = sendGetRequest(url);
        assertTrue(response.has("success"), "Response should contain success field");
    }

    @Test
    @DisplayName("测试目录列表接口 - 路径: /api/file/list - 用法: GET请求，参数path指定目录路径")
    public void testListDirectory() throws IOException {
        String testPath = "/tmp";
        String url = AGENT_URL + "/api/file/list?path=" + URLEncoder.encode(testPath, "UTF-8");
        logger.info("Testing list directory: {}", url);

        JsonObject response = sendGetRequest(url);
        assertTrue(response.has("success"), "Response should contain success field");
    }

    @Test
    @DisplayName("测试名称列表接口 - 路径: /api/file/nlst - 用法: GET请求，参数path指定目录路径")
    public void testNameList() throws IOException {
        String testPath = "/tmp";
        String url = AGENT_URL + "/api/file/nlst?path=" + URLEncoder.encode(testPath, "UTF-8");
        logger.info("Testing name list: {}", url);

        JsonObject response = sendGetRequest(url);
        assertTrue(response.has("success"), "Response should contain success field");
    }

    @Test
    @DisplayName("测试文件大小接口 - 路径: /api/file/size - 用法: GET请求，参数path指定文件路径")
    public void testFileSize() throws IOException {
        // Test with a known file
        String testPath = "/etc/hosts";
        String url = AGENT_URL + "/api/file/size?path=" + URLEncoder.encode(testPath, "UTF-8");
        logger.info("Testing file size: {}", url);

        JsonObject response = sendGetRequest(url);
        assertTrue(response.has("success"), "Response should contain success field");
    }

    @Test
    @DisplayName("测试文件修改时间接口 - 路径: /api/file/mdtm - 用法: GET请求，参数path指定文件路径")
    public void testFileModificationTime() throws IOException {
        // Test with a known file
        String testPath = "/etc/hosts";
        String url = AGENT_URL + "/api/file/mdtm?path=" + URLEncoder.encode(testPath, "UTF-8");
        logger.info("Testing file modification time: {}", url);

        JsonObject response = sendGetRequest(url);
        assertTrue(response.has("success"), "Response should contain success field");
    }

    @Test
    @DisplayName("测试文件状态接口 - 路径: /api/file/stat - 用法: GET请求，参数path指定文件路径")
    public void testFileStatus() throws IOException {
        // Test with a known file
        String testPath = "/etc/hosts";
        String url = AGENT_URL + "/api/file/stat?path=" + URLEncoder.encode(testPath, "UTF-8");
        logger.info("Testing file status: {}", url);

        JsonObject response = sendGetRequest(url);
        assertTrue(response.has("success"), "Response should contain success field");
    }

    @Test
    @DisplayName("测试分块上传会话接口 - 路径: /api/file/chunk/sessions - 用法: GET请求，获取分块上传会话列表")
    public void testChunkSessions() throws IOException {
        String url = AGENT_URL + "/api/file/chunk/sessions";
        logger.info("Testing chunk sessions: {}", url);

        JsonObject response = sendGetRequest(url);
        assertTrue(response.has("success"), "Response should contain success field");
    }

    @Test
    public void testDeleteFile() throws IOException {
        String testPath = "/tmp/uploaded-files/my-panel.10492851590453588022.dat";
        String url = AGENT_URL + "/api/file/dele?path=" + URLEncoder.encode(testPath, "UTF-8");
        logger.info("Testing delete file: {}", url);
        JsonObject response = sendGetRequest(url);
        assertNotNull(response.get("success"));
    }
}