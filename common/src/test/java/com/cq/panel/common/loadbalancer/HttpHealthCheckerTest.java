package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * HTTP健康检查器测试
 */
@DisplayName("HTTP健康检查器测试")
@ExtendWith(MockitoExtension.class)
class HttpHealthCheckerTest {
    
    @Mock
    private HttpClient mockHttpClient;
    
    private HttpHealthChecker httpHealthChecker;
    private Server testServer;
    
    @BeforeEach
    void setUp() {
        // 创建测试服务器
        testServer = new Server("test-server", "localhost", 8080, "http", "zone1");
        testServer.setAlive(true);
        
        // 创建HTTP健康检查器（使用mock客户端）
        httpHealthChecker = new HttpHealthChecker(mockHttpClient, "/health", 5000);
    }
    
    @Test
    @DisplayName("创建HTTP健康检查器")
    void testCreateHttpHealthChecker() {
        assertNotNull(httpHealthChecker);
        assertEquals("httpHealthChecker", httpHealthChecker.getName());
        assertEquals("/health", httpHealthChecker.getHealthCheckPath());
        assertEquals(5000, httpHealthChecker.getTimeoutMs());
    }
    
    @Test
    @DisplayName("创建默认HTTP健康检查器")
    void testCreateDefaultHttpHealthChecker() {
        HttpHealthChecker defaultChecker = new HttpHealthChecker();
        
        assertNotNull(defaultChecker);
        assertEquals("httpHealthChecker", defaultChecker.getName());
        assertEquals("/health", defaultChecker.getHealthCheckPath());
        assertEquals(5000, defaultChecker.getTimeoutMs());
    }
    
    @Test
    @DisplayName("创建自定义配置的HTTP健康检查器")
    void testCreateCustomHttpHealthChecker() {
        HttpHealthChecker customChecker = new HttpHealthChecker(mockHttpClient, "/api/health", 3000);
        
        assertNotNull(customChecker);
        assertEquals("/api/health", customChecker.getHealthCheckPath());
        assertEquals(3000, customChecker.getTimeoutMs());
    }
    
    @Test
    @DisplayName("检查空路径和无效超时时间的默认值")
    void testCreateWithInvalidParameters() {
        HttpHealthChecker checker1 = new HttpHealthChecker(mockHttpClient, null, 5000);
        assertEquals("/health", checker1.getHealthCheckPath());
        
        HttpHealthChecker checker2 = new HttpHealthChecker(mockHttpClient, "/health", -100);
        assertEquals(5000, checker2.getTimeoutMs());
        
        HttpHealthChecker checker3 = new HttpHealthChecker(mockHttpClient, "", 0);
        assertEquals("", checker3.getHealthCheckPath());
        assertEquals(5000, checker3.getTimeoutMs());
    }
    
    @Test
    @DisplayName("检查无效服务器")
    void testCheckInvalidServer() {
        // 测试空服务器
        assertFalse(httpHealthChecker.isHealthy(null));
        
        // 测试不存活的服务器
        Server deadServer = new Server("dead-server", "localhost", 8080, "http", "zone1");
        deadServer.setAlive(false);
        assertFalse(httpHealthChecker.isHealthy(deadServer));
        
        // 测试无效端口的服务器
        Server invalidPortServer = new Server("invalid-port", "localhost", -1, "http", "zone1");
        invalidPortServer.setAlive(true);
        assertFalse(httpHealthChecker.isHealthy(invalidPortServer));
    }
    
    @Test
    @DisplayName("检查健康服务器（模拟成功响应）")
    void testCheckHealthyServer() {
        // 模拟成功的HTTP响应
        HttpResponse<String> successResponse = HttpResponse.ok("{\"status\":\"UP\"}");
        when(mockHttpClient.get(eq("http://localhost:8080/health"), eq(String.class)))
                .thenReturn(successResponse);
        
        // 执行健康检查
        boolean isHealthy = httpHealthChecker.isHealthy(testServer);
        
        // 验证结果
        assertTrue(isHealthy);
        verify(mockHttpClient, times(1)).get(anyString(), eq(String.class));
    }
    
    @Test
    @DisplayName("检查不健康服务器（模拟失败响应）")
    void testCheckUnhealthyServer() {
        // 模拟失败的HTTP响应
        HttpResponse<String> failureResponse = new HttpResponse<>("Service Unavailable", 503, new HashMap<>());
        when(mockHttpClient.get(eq("http://localhost:8080/health"), eq(String.class)))
                .thenReturn(failureResponse);
        
        // 执行健康检查
        boolean isHealthy = httpHealthChecker.isHealthy(testServer);
        
        // 验证结果
        assertFalse(isHealthy);
        verify(mockHttpClient, times(1)).get(anyString(), eq(String.class));
    }
    
    @Test
    @DisplayName("检查服务器（模拟异常）")
    void testCheckServerWithException() {
        // 模拟HTTP客户端抛出异常
        when(mockHttpClient.get(eq("http://localhost:8080/health"), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));
        
        // 执行健康检查
        boolean isHealthy = httpHealthChecker.isHealthy(testServer);
        
        // 验证结果（异常情况下应该返回false）
        assertFalse(isHealthy);
        verify(mockHttpClient, times(1)).get(anyString(), eq(String.class));
    }
    
    @Test
    @DisplayName("检查不同状态码的响应")
    void testCheckDifferentStatusCodes() {
        // 测试2xx状态码（应该返回true）
        testStatusCode(200, true);
        testStatusCode(201, true);
        testStatusCode(204, true);
        testStatusCode(299, true);
        
        // 测试非2xx状态码（应该返回false）
        testStatusCode(400, false);
        testStatusCode(401, false);
        testStatusCode(403, false);
        testStatusCode(404, false);
        testStatusCode(500, false);
        testStatusCode(502, false);
        testStatusCode(503, false);
    }
    
    @Test
    @DisplayName("检查不同协议和端口的服务器")
    void testCheckDifferentProtocolsAndPorts() {
        // 测试HTTPS协议
        Server httpsServer = new Server("https-server", "example.com", 443, "https", "zone1");
        httpsServer.setAlive(true);
        
        HttpResponse<String> successResponse = HttpResponse.ok("{\"status\":\"UP\"}");
        when(mockHttpClient.get(eq("https://example.com:443/health"), eq(String.class)))
                .thenReturn(successResponse);
        
        assertTrue(httpHealthChecker.isHealthy(httpsServer));
        
        // 测试不同端口
        Server customPortServer = new Server("custom-port", "localhost", 9000, "http", "zone1");
        customPortServer.setAlive(true);
        
        when(mockHttpClient.get(eq("http://localhost:9000/health"), eq(String.class)))
                .thenReturn(successResponse);
        
        assertTrue(httpHealthChecker.isHealthy(customPortServer));
    }
    
    @Test
    @DisplayName("检查自定义健康检查路径")
    void testCheckWithCustomHealthPath() {
        // 创建使用自定义路径的健康检查器
        HttpHealthChecker customPathChecker = new HttpHealthChecker(mockHttpClient, "/api/health/status", 5000);
        
        HttpResponse<String> successResponse = HttpResponse.ok("{\"status\":\"UP\"}");
        when(mockHttpClient.get(eq("http://localhost:8080/api/health/status"), eq(String.class)))
                .thenReturn(successResponse);
        
        boolean isHealthy = customPathChecker.isHealthy(testServer);
        
        assertTrue(isHealthy);
        verify(mockHttpClient, times(1)).get(eq("http://localhost:8080/api/health/status"), eq(String.class));
    }
    
    @Test
    @DisplayName("检查空路径的健康检查")
    void testCheckWithEmptyPath() {
        // 创建使用空路径的健康检查器
        HttpHealthChecker emptyPathChecker = new HttpHealthChecker(mockHttpClient, "", 5000);
        
        HttpResponse<String> successResponse = HttpResponse.ok("{\"status\":\"UP\"}");
        when(mockHttpClient.get(eq("http://localhost:8080"), eq(String.class)))
                .thenReturn(successResponse);
        
        boolean isHealthy = emptyPathChecker.isHealthy(testServer);
        
        assertTrue(isHealthy);
        verify(mockHttpClient, times(1)).get(eq("http://localhost:8080"), eq(String.class));
    }
    
    @Test
    @DisplayName("检查根路径的健康检查")
    void testCheckWithRootPath() {
        // 创建使用根路径的健康检查器
        HttpHealthChecker rootPathChecker = new HttpHealthChecker(mockHttpClient, "/", 5000);
        
        HttpResponse<String> successResponse = HttpResponse.ok("{\"status\":\"UP\"}");
        when(mockHttpClient.get(eq("http://localhost:8080/"), eq(String.class)))
                .thenReturn(successResponse);
        
        boolean isHealthy = rootPathChecker.isHealthy(testServer);
        
        assertTrue(isHealthy);
        verify(mockHttpClient, times(1)).get(eq("http://localhost:8080/"), eq(String.class));
    }
    
    /**
     * 辅助方法：测试特定状态码
     */
    private void testStatusCode(int statusCode, boolean expectedResult) {
        HttpResponse<String> response = new HttpResponse<>( "Test",statusCode, new HashMap<>());
        
        // 重置mock，确保每次调用都是独立的
        reset(mockHttpClient);
        when(mockHttpClient.get(eq("http://localhost:8080/health"), eq(String.class)))
                .thenReturn(response);
        
        boolean result = httpHealthChecker.isHealthy(testServer);
        assertEquals(expectedResult, result, 
                "状态码 " + statusCode + " 应该返回 " + expectedResult);
    }
}