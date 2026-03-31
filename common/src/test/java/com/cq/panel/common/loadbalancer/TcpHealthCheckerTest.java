package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TCP健康检查器测试
 */
@DisplayName("TCP健康检查器测试")
class TcpHealthCheckerTest extends BaseMockHttpServerTest {
    
    private TcpHealthChecker tcpHealthChecker;
    
    @BeforeEach
    void setUp() {
        tcpHealthChecker = new TcpHealthChecker(3000);
    }
    
    @Test
    @DisplayName("创建TCP健康检查器")
    void testCreateTcpHealthChecker() {
        assertNotNull(tcpHealthChecker);
        assertEquals("tcpHealthChecker", tcpHealthChecker.getName());
        assertEquals(3000, tcpHealthChecker.getTimeoutMs());
    }
    
    @Test
    @DisplayName("检查无效服务器")
    void testCheckInvalidServer() {
        // 测试空服务器
        assertFalse(tcpHealthChecker.isHealthy(null));
        
        // 测试无效端口的服务器
        Server invalidPortServer = new Server("test", "localhost", -1, "http", "zone1");
        assertFalse(tcpHealthChecker.isHealthy(invalidPortServer));
        
        // 测试超大端口的服务器
        Server largePortServer = new Server("test", "localhost", 70000, "http", "zone1");
        assertFalse(tcpHealthChecker.isHealthy(largePortServer));
    }
    
    @Test
    @DisplayName("检查非TCP协议服务器")
    void testCheckNonTcpProtocolServer() {
        // 测试非TCP协议服务器（如Unix域套接字）
        Server unixSocketServer = new Server("test", "/var/run/socket", 0, "unix", "zone1");
        assertFalse(tcpHealthChecker.isHealthy(unixSocketServer));
        
        // 测试空协议服务器
        Server nullProtocolServer = new Server("test", "localhost", 8080, "", "zone1");
        assertFalse(tcpHealthChecker.isHealthy(nullProtocolServer));
    }
    
    @Test
    @DisplayName("检查TCP协议识别")
    void testTcpProtocolRecognition() {
        TcpHealthChecker checker = new TcpHealthChecker();
        
        // 测试HTTP协议识别
        assertTrue(checker.isTcpProtocol("http"));
        assertTrue(checker.isTcpProtocol("https"));
        assertTrue(checker.isTcpProtocol("HTTP"));
        assertTrue(checker.isTcpProtocol("HTTPS"));
        
        // 测试TCP协议识别
        assertTrue(checker.isTcpProtocol("tcp"));
        assertTrue(checker.isTcpProtocol("TCP"));
        assertTrue(checker.isTcpProtocol("socket"));
        
        // 测试非TCP协议识别
        assertFalse(checker.isTcpProtocol("unix"));
        assertFalse(checker.isTcpProtocol("file"));
        assertFalse(checker.isTcpProtocol("ftp"));
        assertFalse(checker.isTcpProtocol(null));
        assertFalse(checker.isTcpProtocol(""));
        assertFalse(checker.isTcpProtocol("   "));
    }
    
    @Test
    @DisplayName("检查不可达服务器")
    void testCheckUnreachableServer() {
        // 测试不可达的服务器（使用一个不太可能被占用的端口）
        Server unreachableServer = new Server("test", "localhost", 65534, "http", "zone1");
        
        // 这个测试应该返回false，因为端口不可达
        assertFalse(tcpHealthChecker.isHealthy(unreachableServer));
    }
    
    @Test
    @DisplayName("检查本地回环地址")
    void testCheckLocalhost() {
        // 测试本地回环地址（可能会失败，因为可能没有服务在监听）
        Server localhostServer = new Server("test", "127.0.0.1", PORT, "http", "zone1");
        
        // 这个测试结果取决于本地8080端口是否有服务在运行
        // 我们只验证方法能够正常执行，不验证具体结果
        boolean result = tcpHealthChecker.isHealthy(localhostServer);
        
        // 结果应该是boolean值
        assertTrue(result); // 总是为true，只是验证类型
    }
    
    @Test
    @DisplayName("检查健康检查器字符串表示")
    void testToString() {
        String toString = tcpHealthChecker.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("TcpHealthChecker"));
        assertTrue(toString.contains("timeoutMs=3000"));
    }
}