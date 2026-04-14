package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HttpClientFactory独立测试类
 */
@DisplayName("HttpClientFactory测试")
class HttpClientFactoryTest extends BaseMockHttpServerTest {
    
    @ParameterizedTest
    @EnumSource(HttpClientFactory.HttpClientType.class)
    @DisplayName("工厂方法创建不同客户端类型")
    void testFactoryMethods(HttpClientFactory.HttpClientType type) {
        HttpClient client = HttpClientFactory.create(type);
        assertNotNull(client);
        
        // 测试基本功能
        HttpResponse<String> response = client.get(BASE_URL + "/json", String.class);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
    }
    
    @Test
    @DisplayName("创建默认客户端")
    void testCreateDefault() {
        HttpClient client = HttpClientFactory.createDefault();
        assertNotNull(client);
        
        HttpResponse<String> response = client.get(BASE_URL + "/json", String.class);
        assertNotNull(response);
    }
    
    @Test
    @DisplayName("创建快速客户端")
    void testCreateFastClient() {
        HttpClient client = HttpClientFactory.createFast(HttpClientFactory.HttpClientType.SIMPLE);
        assertNotNull(client);
        
        assertEquals(2000, client.getConnectTimeout());
        assertEquals(5000, client.getReadTimeout());
    }
    
    @Test
    @DisplayName("创建高可用客户端")
    void testCreateHighAvailabilityClient() {
        HttpClient client = HttpClientFactory.createHighAvailability(HttpClientFactory.HttpClientType.APACHE);
        assertNotNull(client);
        
        assertEquals(10000, client.getConnectTimeout());
        assertEquals(30000, client.getReadTimeout());
    }
}