package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleHttpClient独立测试类
 */
@DisplayName("SimpleHttpClient测试")
class SimpleHttpClientTest extends BaseMockHttpServerTest {
    
    private HttpClient client;
    
    @BeforeEach
    void setUp() {
        client = new SimpleHttpClient(3000, 5000);
    }
    
    @Test
    @DisplayName("GET请求 - 基本功能")
    void testGetRequest() {
        // 注意：这里使用一个公开的测试API
        HttpResponse<String> response = client.get(BASE_URL + "/json", String.class);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        log.info("response body: {}", response.getBody());
        assertTrue(response.getBody().contains("name"));
    }
    
    @Test
    @DisplayName("GET请求 - 带HTTP头")
    void testGetRequestWithHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "TestClient/1.0");
        headers.put("X-Custom-Header", "custom-value");
        
        HttpResponse<String> response = client.get(BASE_URL + "/custom", headers, String.class);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Custom data response", response.getBody());
        assertTrue(response.getHeaders().containsKey("server"));
        assertTrue(response.getHeaders().containsKey("content-type"));
    }
    
    @Test
    @DisplayName("POST请求 - JSON数据")
    void testPostRequest() {
        String jsonBody = "{\"name\":\"test\",\"value\":123}";
        HttpResponse<String> response = client.post(BASE_URL + "/json", jsonBody, String.class);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("test"));
    }
    
    @Test
    @DisplayName("PUT请求 - 带自定义头")
    void testPutRequestWithHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer token123");
        
        String jsonBody = "{\"id\":1,\"name\":\"updated\"}";
        HttpResponse<String> response = client.put(BASE_URL + "/json", jsonBody, headers, String.class);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
    }
    
    @Test
    @DisplayName("DELETE请求")
    void testDeleteRequest() {
        HttpResponse<String> response = client.delete(BASE_URL + "/delete", String.class);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
    }
    
    @Test
    @DisplayName("通用请求方法")
    void testExecuteMethod() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        
        // 使用PUT方法代替PATCH，因为httpbin.org的PATCH端点可能有问题
        HttpResponse<String> response = client.execute("PUT", BASE_URL + "/put",
            "{\"status\":\"active\"}", headers, String.class);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
    }
    
    @Test
    @DisplayName("超时配置")
    void testTimeoutConfiguration() {
        client.setConnectTimeout(1000);
        client.setReadTimeout(2000);
        
        assertEquals(1000, client.getConnectTimeout());
        assertEquals(2000, client.getReadTimeout());
    }
}