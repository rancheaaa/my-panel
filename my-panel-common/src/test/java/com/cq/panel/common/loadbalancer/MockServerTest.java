package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 模拟服务器测试
 * 使用纯Java内置HttpServer来模拟HTTP服务
 */
class MockServerTest extends BaseMockHttpServerTest {

    @Test
    void testJsonResponseWithSimpleHttpClient() {
        HttpClient client = new SimpleHttpClient();
        
        // 设置较短的超时时间
        client.setConnectTimeout(2000);
        client.setReadTimeout(5000);
        
        HttpResponse<String> response = client.get(BASE_URL + "/json", String.class);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        final Map<String, Object> result = GSON.fromJson(response.getBody(), MAP_TYPE_TOKEN);
        assertEquals("test", result.get("name"), "name field should be test: " + result.get("name"));

        // 检查响应头
        assertNotNull(response.getHeaders());
        assertTrue(response.getHeaders().containsKey("content-type"), 
            "Headers should contain content-type: " + response.getHeaders());
        assertEquals("application/json", response.getHeaders().get("content-type"));
    }
    
    @Test
    void testXmlResponseWithApacheHttpClient() {
        HttpClient client = new ApacheHttpClient();
        
        // 设置较短的超时时间
        client.setConnectTimeout(2000);
        client.setReadTimeout(5000);
        
        HttpResponse<String> response = client.get(BASE_URL + "/xml", String.class);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("<name>test</name>"), 
            "Response should contain name tag: " + response.getBody());
        
        // 检查响应头
        assertNotNull(response.getHeaders());
        assertTrue(response.getHeaders().containsKey("Content-type"), 
            "Headers should contain Content-type: " + response.getHeaders());
        assertEquals("application/xml", response.getHeaders().get("Content-type"));
    }
    
    @Test
    void testTextResponseDeserialization() {
        HttpClient client = new SimpleHttpClient();
        
        // 设置较短的超时时间
        client.setConnectTimeout(2000);
        client.setReadTimeout(5000);
        
        // 测试文本到字符串
        HttpResponse<String> stringResponse = client.get(BASE_URL + "/text", String.class);
        assertEquals("Hello World", stringResponse.getBody());
        
        // 测试文本到整数
        HttpResponse<Integer> intResponse = client.get(BASE_URL + "/text/number", Integer.class);
        assertEquals(42, intResponse.getBody());
        
        // 测试文本到布尔值
        HttpResponse<Boolean> boolResponse = client.get(BASE_URL + "/text/boolean", Boolean.class);
        assertEquals(true, boolResponse.getBody());
    }
    
    @Test
    void testCustomContentType() {
        HttpClient client = new ApacheHttpClient();
        
        // 设置较短的超时时间
        client.setConnectTimeout(2000);
        client.setReadTimeout(5000);
        
        HttpResponse<String> response = client.get(BASE_URL + "/custom", String.class);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        
        // 检查响应头
        assertNotNull(response.getHeaders());
        assertTrue(response.getHeaders().containsKey("Content-type"), 
            "Headers should contain Content-type: " + response.getHeaders());
        assertEquals("custom/data", response.getHeaders().get("Content-type"));
        assertEquals("Custom data response", response.getBody());
    }
    
    @Test
    void testErrorResponse() {
        HttpClient client = new SimpleHttpClient();
        
        // 设置较短的超时时间
        client.setConnectTimeout(2000);
        client.setReadTimeout(5000);
        
        HttpResponse<String> response = client.get(BASE_URL + "/error", String.class);
        
        assertNotNull(response);
        assertEquals(500, response.getStatusCode());
    }
    
    @Test
    void testPostRequestWithJsonBody() {
        HttpClient client = new ApacheHttpClient();
        
        // 设置较短的超时时间
        client.setConnectTimeout(2000);
        client.setReadTimeout(5000);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("id", 1);
        requestBody.put("name", "test");
        requestBody.put("active", true);
        
        HttpResponse<String> response = client.post(BASE_URL + "/post", requestBody, String.class);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // 检查响应内容
        String body = response.getBody();
        final Map<String, Object> result = GSON.fromJson(body, MAP_TYPE_TOKEN);
        assertEquals("POST", result.get("method"), "method field should be test: " + result.get("name"));
        assertEquals(true, result.get("received"), "received field should be true: " + result.get("active"));
    }
    
    @Test
    void testResponseHeaders() {
        HttpClient client = new SimpleHttpClient();
        
        // 设置较短的超时时间
        client.setConnectTimeout(2000);
        client.setReadTimeout(5000);
        
        HttpResponse<String> response = client.get(BASE_URL + "/json", String.class);
        
        assertNotNull(response.getHeaders());
        assertTrue(response.getHeaders().containsKey("server"),
            "Headers should contain Server: " + response.getHeaders());
        assertEquals("MockServer/1.0", response.getHeaders().get("server"));
    }
    
    @Test
    void testPutRequestWithCustomHeaders() {
        HttpClient client = new ApacheHttpClient();
        
        // 设置较短的超时时间
        client.setConnectTimeout(2000);
        client.setReadTimeout(5000);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer test-token");
        headers.put("X-Custom-Header", "custom-value");
        
        String jsonBody = "{\"id\":1,\"name\":\"updated\"}";
        HttpResponse<String> response = client.put(BASE_URL + "/put", jsonBody, headers, String.class);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // 检查响应内容
        String body = response.getBody();
        final Map<String, Object> result = GSON.fromJson(body, MAP_TYPE_TOKEN);
        assertEquals("PUT", result.get("method"), "name field should be updated: " + result.get("name"));
    }
    
    @Test
    void testDeleteRequest() {
        HttpClient client = new SimpleHttpClient();
        
        // 设置较短的超时时间
        client.setConnectTimeout(2000);
        client.setReadTimeout(5000);
        
        HttpResponse<String> response = client.delete(BASE_URL + "/delete", String.class);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertEquals("Deleted successfully", response.getBody());
    }
    
    @Test
    void testExecuteMethodWithPatch() {
        HttpClient client = new ApacheHttpClient();
        
        // 设置较短的超时时间
        client.setConnectTimeout(2000);
        client.setReadTimeout(5000);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        
        HttpResponse<String> response = client.execute("PATCH", BASE_URL + "/patch", 
            "{\"status\":\"active\"}", headers, String.class);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // 检查响应内容
        String body = response.getBody();
        final Map<String, Object> result = GSON.fromJson(body, MAP_TYPE_TOKEN);
        assertEquals("PATCH", result.get("method"), "method field should be PATCH: " + result.get("method"));
        assertEquals("active", result.get("status"), "status field should be active: " + result.get("status"));
        assertEquals(true, result.get("received"), "received field should be true: " + result.get("received"));
    }
    
    @Test
    void testBinaryResponseHandling() {
        HttpClient client = new SimpleHttpClient();
        
        // 设置较短的超时时间
        client.setConnectTimeout(2000);
        client.setReadTimeout(5000);
        
        // 测试二进制数据响应
        HttpResponse<byte[]> response = client.get(BASE_URL + "/binary", byte[].class);
        
        assertNotNull(response);
        assertEquals(500, response.getStatusCode());
    }
    
    @Test
    void testTimeoutConfiguration() {
        HttpClient client = new SimpleHttpClient();
        
        // 设置较短的超时时间
        client.setConnectTimeout(1000);
        client.setReadTimeout(2000);
        
        assertEquals(1000, client.getConnectTimeout());
        assertEquals(2000, client.getReadTimeout());
        
        // 测试正常请求
        HttpResponse<String> response = client.get(BASE_URL + "/json", String.class);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
    }
    
    @Test
    void testResponseDeserializerWithJson() {
        HttpClient client = new ApacheHttpClient();
        
        // 设置较短的超时时间
        client.setConnectTimeout(2000);
        client.setReadTimeout(5000);
        
        // 获取JSON响应
        HttpResponse<String> stringResponse = client.get(BASE_URL + "/json", String.class);
        
        // 使用ResponseDeserializer进行反序列化
        Map<?, ?> deserialized = ResponseDeserializer.deserialize(
            stringResponse.getBody(), 
            stringResponse.getHeaders().get("content-type"), 
            Map.class
        );
        
        assertNotNull(deserialized);
        assertEquals("test", deserialized.get("name"));
        assertEquals(25.0, deserialized.get("age"));
        assertEquals(true, deserialized.get("active"));
    }
    
    @Test
    void testResponseDeserializerWithText() {
        // 测试文本反序列化
        String textResponse = "Hello World";
        String contentType = "text/plain";
        
        // 反序列化为String
        String stringResult = ResponseDeserializer.deserialize(textResponse, contentType, String.class);
        assertEquals("Hello World", stringResult);
        
        // 反序列化为Integer
        Integer intResult = ResponseDeserializer.deserialize("123", contentType, Integer.class);
        assertEquals(123, intResult);
        
        // 反序列化为Boolean
        Boolean boolResult = ResponseDeserializer.deserialize("true", contentType, Boolean.class);
        assertEquals(true, boolResult);
    }
}