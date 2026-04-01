package com.cq.panel.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PortUtils测试类
 */
@DisplayName("PortUtils测试")
class PortUtilsTest {
    
    private static ServerSocket testServer;
    private static int testPort;
    
    @BeforeAll
    static void setUp() throws IOException {
        // 启动一个测试服务器用于端口检测
        testServer = new ServerSocket(0); // 使用0让系统分配可用端口
        testPort = testServer.getLocalPort();
    }
    
    @AfterAll
    static void tearDown() throws IOException {
        if (testServer != null) {
            testServer.close();
        }
    }
    
    @Test
    @DisplayName("端口可用性检查 - 可用端口")
    void testIsPortAvailableWithAvailablePort() {
        // 找一个可用端口
        int availablePort = PortUtils.probeAvailablePort(30000, 100);
        assertTrue(availablePort > 0, "应该找到可用端口");
        
        // 验证端口可用
        assertTrue(PortUtils.isPortAvailable(availablePort), "端口应该可用");
    }
    
    @Test
    @DisplayName("端口可用性检查 - 已占用端口")
    void testIsPortAvailableWithOccupiedPort() {
        // 使用测试服务器的端口（已被占用）
        assertFalse(PortUtils.isPortAvailable(testPort), "被占用的端口应该返回false");
    }
    
    @Test
    @DisplayName("端口探测 - 正常情况")
    void testProbeAvailablePort() {
        int startPort = 30000;
        int maxSteps = 10;
        
        int availablePort = PortUtils.probeAvailablePort(startPort, maxSteps);
        
        assertTrue(availablePort >= startPort && availablePort < startPort + maxSteps, 
            "找到的端口应该在指定范围内");
        assertTrue(PortUtils.isPortAvailable(availablePort), "找到的端口应该可用");
    }
    
    @Test
    @DisplayName("端口探测 - 无效参数")
    void testProbeAvailablePortWithInvalidParams() {
        // 测试负数的最大步数
        int result = PortUtils.probeAvailablePort(30000, -1);
        assertTrue(result > 0, "即使参数无效也应该返回有效结果");
        
        // 测试0步数
        result = PortUtils.probeAvailablePort(30000, 0);
        assertTrue(result > 0, "即使参数无效也应该返回有效结果");
    }
    
    @Test
    @DisplayName("端口检测 - 成功连接")
    void testPortDetectSuccess() {
        PortUtils.PortDetectEnum result = PortUtils.portDetect("localhost", testPort, 5000);
        assertEquals(PortUtils.PortDetectEnum.SUCCESS, result, "应该成功连接到测试服务器");
    }
    
    @Test
    @DisplayName("端口检测 - 端口未监听")
    void testPortDetectPortNotListen() {
        // 找一个未使用的端口
        int unusedPort = PortUtils.probeAvailablePort(31000, 100);
        
        PortUtils.PortDetectEnum result = PortUtils.portDetect("localhost", unusedPort, 5000);
        assertEquals(PortUtils.PortDetectEnum.PORT_NOT_LISTEN, result, 
            "未监听的端口应该返回PORT_NOT_LISTEN");
    }
    
    @Test
    @DisplayName("端口检测 - 连接超时")
    void testPortDetectConnectionTimeout() {
        // 使用一个可能不存在的IP和端口，设置很短的超时时间
        PortUtils.PortDetectEnum result = PortUtils.portDetect("192.168.255.255", 8080, 1);
        
        // 可能是超时或连接失败，取决于网络环境
        assertTrue(result == PortUtils.PortDetectEnum.CONNECTION_TIMEOUT || 
                  result == PortUtils.PortDetectEnum.CONNECTION_FAILED ||
                  result == PortUtils.PortDetectEnum.HOST_UNREACHABLE,
                  "超时或不可达的主机应该返回相应的错误码");
    }
    
    @Test
    @DisplayName("端口检测 - 未知主机")
    void testPortDetectUnknownHost() {
        PortUtils.PortDetectEnum result = PortUtils.portDetect("nonexistent-host-12345.local", 8080, 5000);
        assertEquals(PortUtils.PortDetectEnum.UNKNOWN_HOST, result, 
            "未知主机应该返回UNKNOWN_HOST");
    }
    
    @Test
    @DisplayName("端口检测 - 无效端口号")
    void testPortDetectInvalidPort() {
        PortUtils.PortDetectEnum result = PortUtils.portDetect("localhost", 70000, 5000);
        assertEquals(PortUtils.PortDetectEnum.INVALID_PORT, result, 
            "超出范围的端口号应该返回INVALID_PORT");
        
        // 负数的端口号也应该被视为无效端口
        result = PortUtils.portDetect("localhost", -1, 5000);
        assertEquals(PortUtils.PortDetectEnum.INVALID_PORT, result, 
            "负数的端口号应该返回INVALID_PORT");
    }
    
    @Test
    @DisplayName("端口检测 - 非法参数")
    void testPortDetectIllegalArgument() {
        // 测试null主机名（真正的非法参数）
        PortUtils.PortDetectEnum result = PortUtils.portDetect(null, 8080, 5000);
        assertEquals(PortUtils.PortDetectEnum.ILLEGAL_ARGUMENT, result, 
            "null主机名应该返回ILLEGAL_ARGUMENT");
    }
    
    @Test
    @DisplayName("端口检测 - 快速检测方法")
    void testPortDetectFast() {
        // 测试成功的快速检测
        boolean success = PortUtils.portDetectFast("localhost", testPort, 5000);
        assertTrue(success, "快速检测应该返回true");
        
        // 测试失败的快速检测
        int unusedPort = PortUtils.probeAvailablePort(32000, 100);
        boolean failure = PortUtils.portDetectFast("localhost", unusedPort, 5000);
        assertFalse(failure, "未监听的端口快速检测应该返回false");
    }
    
    @Test
    @DisplayName("枚举值完整性测试")
    void testPortDetectEnumValues() {
        // 验证所有枚举值都存在
        PortUtils.PortDetectEnum[] values = PortUtils.PortDetectEnum.values();
        
        assertTrue(values.length >= 13, "应该有足够的枚举值覆盖各种错误情况");
        
        // 验证关键枚举值存在
        assertNotNull(PortUtils.PortDetectEnum.SUCCESS);
        assertNotNull(PortUtils.PortDetectEnum.PORT_NOT_LISTEN);
        assertNotNull(PortUtils.PortDetectEnum.CONNECTION_TIMEOUT);
        assertNotNull(PortUtils.PortDetectEnum.UNKNOWN_HOST);
        assertNotNull(PortUtils.PortDetectEnum.INVALID_PORT);
        assertNotNull(PortUtils.PortDetectEnum.UNKNOWN_ERROR);
    }
    
    @Test
    @DisplayName("边界条件测试 - 极小超时时间")
    void testPortDetectWithMinimalTimeout() {
        PortUtils.PortDetectEnum result = PortUtils.portDetect("localhost", testPort, 1);
        
        // 极小超时时间可能导致超时或成功，取决于系统性能
        assertTrue(result == PortUtils.PortDetectEnum.SUCCESS || 
                  result == PortUtils.PortDetectEnum.CONNECTION_TIMEOUT,
                  "极小超时时间应该返回SUCCESS或CONNECTION_TIMEOUT");
    }
    
    @Test
    @DisplayName("边界条件测试 - 极大超时时间")
    void testPortDetectWithLargeTimeout() {
        PortUtils.PortDetectEnum result = PortUtils.portDetect("localhost", testPort, 30000);
        assertEquals(PortUtils.PortDetectEnum.SUCCESS, result, 
            "极大超时时间应该成功连接");
    }
    
    @Test
    @DisplayName("网络不可达测试")
    void testPortDetectNetworkUnreachable() {
        // 使用一个保留的IP地址，模拟网络不可达
        PortUtils.PortDetectEnum result = PortUtils.portDetect("192.0.2.1", 8080, 5000);
        
        // 可能是网络不可达或其他错误，取决于网络环境
        assertTrue(result == PortUtils.PortDetectEnum.NETWORK_UNREACHABLE ||
                  result == PortUtils.PortDetectEnum.HOST_UNREACHABLE ||
                  result == PortUtils.PortDetectEnum.CONNECTION_TIMEOUT,
                  "网络不可达应该返回相应的错误码");
    }
    
    @Test
    @DisplayName("Socket错误测试")
    void testPortDetectSocketError() {
        // 测试一个可能引起Socket异常的情况
        // 使用本地回环地址和一个可能被防火墙阻止的端口
        PortUtils.PortDetectEnum result = PortUtils.portDetect("127.0.0.1", 1, 5000);
        
        // 可能是端口未监听或Socket错误
        assertTrue(result == PortUtils.PortDetectEnum.PORT_NOT_LISTEN ||
                  result == PortUtils.PortDetectEnum.SOCKET_ERROR,
                  "低端口号可能引起Socket错误");
    }
}