package com.cq.agent.client.upload;

import com.cq.agent.client.RemoteAgentInfo;
import com.cq.agent.client.Util;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class UtilTest {

    @Test
    @DisplayName("测试正常情况 - 标准IPv4地址")
    void testResolveRemoteAgentInfo_NormalIPv4() {
        String input = "192.168.1.100:7777@root:/tmp/upload";
        
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null");
        assertEquals("192.168.1.100", result.getIp(), "IP should match");
        assertEquals(7777, result.getPort(), "Port should match");
        assertEquals("root", result.getUsername(), "Username should match");
        assertEquals("/tmp/upload", result.getDestFilePath(), "Dest file path should match");
    }

    @Test
    @DisplayName("测试正常情况 - HTTP格式的IPv4地址")
    void testResolveRemoteAgentInfo_HttpIPv4() {
        String input = "http://192.168.1.100:7777@root:/tmp/upload";
        
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null");
        assertEquals("192.168.1.100", result.getIp(), "IP should match");
        assertEquals(7777, result.getPort(), "Port should match");
        assertEquals("root", result.getUsername(), "Username should match");
        assertEquals("/tmp/upload", result.getDestFilePath(), "Dest file path should match");
    }

    @Test
    @DisplayName("测试正常情况 - HTTPS格式的IPv4地址")
    void testResolveRemoteAgentInfo_HttpsIPv4() {
        String input = "https://192.168.1.100:7777@root:/tmp/upload";
        
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null");
        assertEquals("192.168.1.100", result.getIp(), "IP should match");
        assertEquals(7777, result.getPort(), "Port should match");
        assertEquals("root", result.getUsername(), "Username should match");
        assertEquals("/tmp/upload", result.getDestFilePath(), "Dest file path should match");
    }

    @Test
    @DisplayName("测试正常情况 - HTTP格式的域名")
    void testResolveRemoteAgentInfo_HttpDomainName() {
        String input = "http://example.com:8080@user:/home/user/file";
        
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null");
        assertEquals("example.com", result.getIp(), "Domain should match");
        assertEquals(8080, result.getPort(), "Port should match");
        assertEquals("user", result.getUsername(), "Username should match");
        assertEquals("/home/user/file", result.getDestFilePath(), "Dest file path should match");
    }

    @Test
    @DisplayName("测试正常情况 - HTTP格式的localhost")
    void testResolveRemoteAgentInfo_HttpLocalhost() {
        String input = "http://localhost:9000@admin:/var/www/uploads";
        
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null");
        assertEquals("localhost", result.getIp(), "IP should be localhost");
        assertEquals(9000, result.getPort(), "Port should match");
        assertEquals("admin", result.getUsername(), "Username should match");
        assertEquals("/var/www/uploads", result.getDestFilePath(), "Dest file path should match");
    }

    @Test
    @DisplayName("测试边界情况 - 端口为0")
    void testResolveRemoteAgentInfo_PortZero() {
        String input = "192.168.1.100:0@root:/tmp/upload";
        
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null");
        assertEquals("192.168.1.100", result.getIp());
        assertEquals(0, result.getPort(), "Port should be 0");
        assertEquals("root", result.getUsername());
        assertEquals("/tmp/upload", result.getDestFilePath());
    }

    @Test
    @DisplayName("测试边界情况 - 最大端口号65535")
    void testResolveRemoteAgentInfo_MaxPort() {
        String input = "192.168.1.100:65535@root:/tmp/upload";
        
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null");
        assertEquals("192.168.1.100", result.getIp());
        assertEquals(65535, result.getPort(), "Port should be 65535");
        assertEquals("root", result.getUsername());
        assertEquals("/tmp/upload", result.getDestFilePath());
    }

    @Test
    @DisplayName("测试边界情况 - 路径包含特殊字符")
    void testResolveRemoteAgentInfo_SpecialCharactersInPath() {
        String input = "192.168.1.100:7777@user:/home/user/my-file_123.txt";
        
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null");
        assertEquals("192.168.1.100", result.getIp());
        assertEquals(7777, result.getPort());
        assertEquals("user", result.getUsername());
        assertEquals("/home/user/my-file_123.txt", result.getDestFilePath());
    }

    @Test
    @DisplayName("测试边界情况 - 空格在路径中")
    void testResolveRemoteAgentInfo_SpacesInPath() {
        String input = "192.168.1.100:7777@user:/home/user/my folder/file.txt";
        
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null");
        assertEquals("192.168.1.100", result.getIp());
        assertEquals(7777, result.getPort());
        assertEquals("user", result.getUsername());
        assertEquals("/home/user/my folder/file.txt", result.getDestFilePath());
    }

    @Test
    @DisplayName("测试异常情况 - null输入")
    @SuppressWarnings("all")
    void testResolveRemoteAgentInfo_NullInput() {
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(null);
        
        assertNull(result, "Result should be null for null input");
    }

    @Test
    @DisplayName("测试异常情况 - 空字符串")
    void testResolveRemoteAgentInfo_EmptyString() {
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo("");
        
        assertNull(result, "Result should be null for empty string");
    }

    @Test
    @DisplayName("测试异常情况 - 只有空格的字符串")
    void testResolveRemoteAgentInfo_BlankString() {
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo("   ");
        
        assertNull(result, "Result should be null for blank string");
    }

    @Test
    @DisplayName("测试异常情况 - 缺少@符号")
    void testResolveRemoteAgentInfo_MissingAtSymbol() {
        String input = "192.168.1.100:7777root:/tmp/upload";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for missing @ symbol"
        );
        
        assertTrue(exception.getMessage().contains("Invalid remote agent info"),
                   "Error message should mention invalid format");
    }

    @Test
    @DisplayName("测试异常情况 - 多个@符号")
    void testResolveRemoteAgentInfo_MultipleAtSymbols() {
        String input = "192.168.1.100:7777@root@extra:/tmp/upload";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for multiple @ symbols"
        );
        
        assertTrue(exception.getMessage().contains("Invalid remote agent info"),
                   "Error message should mention invalid format");
    }

    @Test
    @DisplayName("测试异常情况 - ip:port部分为空")
    void testResolveRemoteAgentInfo_EmptyIpPort() {
        String input = "@root:/tmp/upload";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for empty ip:port"
        );
        
        assertTrue(exception.getMessage().contains("ip:port can't be empty"),
                   "Error message should mention empty ip:port");
    }

    @Test
    @DisplayName("测试异常情况 - username:destFilePath部分为空")
    void testResolveRemoteAgentInfo_EmptyUsernameDest() {
        String input = "192.168.1.100:7777@";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for empty username:destFilePath"
        );
        
        assertTrue(exception.getMessage().contains("username:destFilePath"),
                   "Error message should mention username:destFilePath");
    }

    @Test
    @DisplayName("测试异常情况 - ip:port缺少冒号")
    void testResolveRemoteAgentInfo_MissingColonInIpPort() {
        String input = "192.168.1.1007777@root:/tmp/upload";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for missing colon in ip:port"
        );
        
        assertTrue(exception.getMessage().contains("Invalid remote agent info format"),
                   "Error message should mention invalid format");
    }

    @Test
    @DisplayName("测试异常情况 - username部分为空")
    void testResolveRemoteAgentInfo_EmptyUsername() {
        String input = "192.168.1.100:7777@:/tmp/upload";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for empty username"
        );
        
        assertTrue(exception.getMessage().contains("username can't be empty"),
                   "Error message should mention empty username");
    }

    @Test
    @DisplayName("测试异常情况 - destFilePath部分为空")
    void testResolveRemoteAgentInfo_EmptyDestFilePath() {
        String input = "192.168.1.100:7777@root:";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for empty destFilePath"
        );
        
        assertTrue(exception.getMessage().contains("destFilePath can't be empty"),
                   "Error message should mention empty destFilePath");
    }

    @Test
    @DisplayName("测试异常情况 - HTTP格式无效URL")
    void testResolveRemoteAgentInfo_HttpInvalidUrl() {
        String input = "http://192.168.1.100:7777@root:/tmp/upload";
        
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null");
        assertEquals("192.168.1.100", result.getIp(), "IP should match");
        assertEquals(7777, result.getPort(), "Port should match");
        assertEquals("root", result.getUsername(), "Username should match");
        assertEquals("/tmp/upload", result.getDestFilePath(), "Dest file path should match");
    }

    @Test
    @DisplayName("测试异常情况 - HTTP格式缺少端口号")
    void testResolveRemoteAgentInfo_HttpMissingPort() {
        String input = "http://192.168.1.100@root:/tmp/upload";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for HTTP format without port"
        );
        
        assertTrue(exception.getMessage().contains("Port not specified in URL"),
                   "Error message should mention missing port");
    }

    @Test
    @DisplayName("测试异常情况 - HTTP格式端口号不是数字")
    void testResolveRemoteAgentInfo_HttpNonNumericPort() {
        String input = "http://192.168.1.100:abc@root:/tmp/upload";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for non-numeric port in HTTP format"
        );
        
        assertTrue(exception.getMessage().contains("Invalid URL format"),
                   "Error message should mention invalid URL format");
    }

    @Test
    @DisplayName("测试异常情况 - HTTP格式端口号为负数")
    void testResolveRemoteAgentInfo_HttpNegativePort() {
        String input = "http://192.168.1.100:-1@root:/tmp/upload";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for negative port in HTTP format"
        );
        
        assertTrue(exception.getMessage().contains("Invalid URL format") || exception.getMessage().contains("Port"),
                   "Error message should mention URL format or Port");
    }

    @Test
    @DisplayName("测试异常情况 - HTTP格式端口号超出范围")
    void testResolveRemoteAgentInfo_HttpPortOutOfRange() {
        String input = "http://192.168.1.100:99999@root:/tmp/upload";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for out of range port in HTTP format"
        );
        
        assertTrue(exception.getMessage().contains("Port must be between 0 and 65535"),
                   "Error message should mention port range");
    }

    @Test
    @DisplayName("测试异常情况 - 端口不是数字")
    void testResolveRemoteAgentInfo_NonNumericPort() {
        String input = "192.168.1.100:abc@root:/tmp/upload";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for non-numeric port"
        );
        
        assertTrue(exception.getMessage().contains("port must be a number"),
                   "Error message should mention port must be a number");
    }

    @Test
    @DisplayName("测试异常情况 - 端口为负数")
    void testResolveRemoteAgentInfo_NegativePort() {
        String input = "192.168.1.100:-1@root:/tmp/upload";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for negative port"
        );
        
        assertTrue(exception.getMessage().contains("port must be between 0 and 65535"),
                   "Error message should mention port range");
    }

    @Test
    @DisplayName("测试异常情况 - 端口超出范围")
    void testResolveRemoteAgentInfo_PortOutOfRange() {
        String input = "192.168.1.100:99999@root:/tmp/upload";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for out of range port"
        );
        
        assertTrue(exception.getMessage().contains("port must be between 0 and 65535"),
                   "Error message should mention port range");
    }

    @ParameterizedTest
    @DisplayName("测试参数化 - 各种有效格式")
    @CsvSource({
        "192.168.1.1:8080@root:/tmp/file.txt",
        "http://192.168.1.1:8080@root:/tmp/file.txt",
        "https://192.168.1.1:8080@root:/tmp/file.txt",
        "10.0.0.1:9000@user:/home/user/data",
        "http://10.0.0.1:9000@user:/home/user/data",
        "172.16.0.1:7777@admin:/var/log/app.log",
        "http://172.16.0.1:7777@admin:/var/log/app.log",
        "localhost:3000@developer:/workspace/project",
        "http://localhost:3000@developer:/workspace/project"
    })
    void testResolveRemoteAgentInfo_ValidFormats(String input) {
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null for valid input: " + input);
        assertNotNull(result.getIp(), "IP should not be null");
        assertTrue(result.getPort() > 0, "Port should be positive");
        assertNotNull(result.getUsername(), "Username should not be null");
        assertNotNull(result.getDestFilePath(), "Dest file path should not be null");
    }

    @ParameterizedTest
    @DisplayName("测试参数化 - 各种无效格式")
    @ValueSource(strings = {
        "192.168.1.100@root:/tmp/upload",
        "192.168.1.100:7777root:/tmp/upload",
        "192.168.1.100:7777@:/tmp/upload",
        "192.168.1.100:7777@root:",
        "192.168.1.100:7777@root",
        "@root:/tmp/upload",
        "192.168.1.100:7777@"
    })
    void testResolveRemoteAgentInfo_InvalidFormats(String input) {
        assertThrows(
            IllegalArgumentException.class,
            () -> Util.resolveRemoteAgentInfo(input),
            "Should throw IllegalArgumentException for invalid input: " + input
        );
    }

    @Test
    @DisplayName("测试边界情况 - 长路径名")
    void testResolveRemoteAgentInfo_LongPath() {
        String longPath = "/very/long/path/that/goes/deep/into/the/directory/structure/" +
                        "with/many/subdirectories/and/a/very/long/filename/" +
                        "that/contains/many/characters/and/numbers/1234567890.txt";
        String input = "192.168.1.100:7777@user:" + longPath;
        
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null for long path");
        assertEquals(longPath, result.getDestFilePath(), "Long path should be preserved");
    }

    @Test
    @DisplayName("测试边界情况 - 用户名包含数字和特殊字符")
    void testResolveRemoteAgentInfo_UsernameWithSpecialChars() {
        String input = "192.168.1.100:7777@user123_456:/tmp/upload";
        
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null");
        assertEquals("user123_456", result.getUsername(), "Username with special chars should be preserved");
    }

    @Test
    @DisplayName("测试边界情况 - 相对路径")
    void testResolveRemoteAgentInfo_RelativePath() {
        String input = "192.168.1.100:7777@user:./relative/path/file.txt";
        
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null for relative path");
        assertEquals("./relative/path/file.txt", result.getDestFilePath(), "Relative path should be preserved");
    }

    @Test
    @DisplayName("测试边界情况 - Windows风格路径")
    void testResolveRemoteAgentInfo_WindowsStylePath() {
        String input = "192.168.1.100:7777@user:C:/Users/user/Documents/file.txt";
        
        RemoteAgentInfo result = Util.resolveRemoteAgentInfo(input);
        
        assertNotNull(result, "Result should not be null for Windows style path");
        assertEquals("C:/Users/user/Documents/file.txt", result.getDestFilePath(), "Windows path should be preserved");
    }
}