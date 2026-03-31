package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResponseDeserializer独立测试类
 */
@DisplayName("ResponseDeserializer测试")
class ResponseDeserializerTest {
    
    @Test
    @DisplayName("JSON反序列化")
    void testJsonDeserialization() {
        String jsonResponse = "{\"name\":\"test\",\"age\":25,\"active\":true}";
        String contentType = "application/json";
        
        // 反序列化为Map
        Map<?, ?> result = ResponseDeserializer.deserialize(jsonResponse, contentType, Map.class);
        
        assertNotNull(result);
        assertEquals("test", result.get("name"));
        assertEquals(25.0, result.get("age")); // Gson将整数转换为double
        assertEquals(true, result.get("active"));
    }
    
    @Test
    @DisplayName("文本反序列化")
    void testTextDeserialization() {
        String textResponse = "Hello World";
        String contentType = "text/plain";
        
        // 反序列化为String
        String result = ResponseDeserializer.deserialize(textResponse, contentType, String.class);
        assertEquals("Hello World", result);
        
        // 反序列化为Integer
        Integer intResult = ResponseDeserializer.deserialize("123", contentType, Integer.class);
        assertEquals(123, intResult);
        
        // 反序列化为Boolean
        Boolean boolResult = ResponseDeserializer.deserialize("true", contentType, Boolean.class);
        assertEquals(true, boolResult);
    }
    
    @Test
    @DisplayName("二进制数据处理")
    void testBinaryDeserialization() {
        String binaryData = "binary content";
        String contentType = "application/octet-stream";
        
        // 反序列化为Base64字符串
        String base64Result = ResponseDeserializer.deserialize(binaryData, contentType, String.class);
        assertNotNull(base64Result);
        
        // 反序列化为字节数组
        byte[] byteResult = ResponseDeserializer.deserialize(binaryData, contentType, byte[].class);
        assertNotNull(byteResult);
        assertEquals("binary content", new String(byteResult));
    }
    
    @Test
    @DisplayName("空响应处理")
    @SuppressWarnings("all")
    void testEmptyResponse() {
        String result = ResponseDeserializer.deserialize("", "application/json", String.class);
        assertNull(result);
        
        result = ResponseDeserializer.deserialize(null, "application/json", String.class);
        assertNull(result);
    }
    
    @Test
    @DisplayName("Content-Type自动识别")
    void testContentTypeAutoDetection() {
        String jsonResponse = "{\"message\":\"success\"}";

        // 测试不同Content-Type格式
        Map<String, String> headers1 = Map.of("Content-Type", "application/json; charset=utf-8");
        Map<?, ?> result1 = ResponseDeserializer.deserialize(jsonResponse, headers1, Map.class);
        assertEquals("success", result1.get("message"));
        
        Map<String, String> headers2 = Map.of("Content-Type", "application/json");
        Map<?, ?> result2 = ResponseDeserializer.deserialize(jsonResponse, headers2, Map.class);
        assertEquals("success", result2.get("message"));
    }
}