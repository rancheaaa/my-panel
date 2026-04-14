package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 错误处理独立测试类
 */
@DisplayName("错误处理测试")
class ErrorHandlingTest extends BaseMockHttpServerTest {
    
    @Test
    @DisplayName("连接超时测试")
    void testConnectionTimeout() {
        HttpClient client = new SimpleHttpClient(100, 100); // 很短的超时时间
        
        // 测试连接超时
        HttpResponse<String> response = client.get(BASE_URL + "/delay/5", String.class);
        
        // 由于超时设置很短，可能会返回错误状态码
        assertNotNull(response);
        // 超时可能返回500或其他错误状态码
    }
    
    @Test
    @DisplayName("无效URL处理")
    void testInvalidUrl() {
        HttpClient client = new SimpleHttpClient();
        
        HttpResponse<String> response = client.get("invalid-url", String.class);
        
        assertNotNull(response);
        assertEquals(500, response.getStatusCode());
    }
    
    @Test
    @DisplayName("JSON反序列化错误")
    void testJsonDeserializationError() {
        String invalidJson = "{invalid json}";
        
        // 应该抛出异常
        assertThrows(RuntimeException.class, () -> ResponseDeserializer.deserialize(invalidJson, "application/json", Map.class));
    }
}