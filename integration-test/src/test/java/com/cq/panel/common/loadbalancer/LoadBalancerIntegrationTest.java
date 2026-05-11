package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 负载均衡客户端集成独立测试类
 */
@DisplayName("负载均衡客户端集成测试")
class LoadBalancerIntegrationTest {
    
    @Test
    @DisplayName("负载均衡器与HttpClient集成")
    void testLoadBalancerWithHttpClient() {
        // 创建服务器列表
        Server server1 = new Server("server1", "httpbin.org", 443, "https", "zone1");
        Server server2 = new Server("server2", "jsonplaceholder.typicode.com", 443, "https", "zone1");
        
        StaticServerList serverList = new StaticServerList();
        serverList.addServer("test-service", server1);
        serverList.addServer("test-service", server2);
        
        // 创建负载均衡器
        LoadBalancer loadBalancer = new RoundRobinLoadBalancer();
        
        // 使用Apache HttpClient
        HttpClient httpClient = new ApacheHttpClient();
        
        // 创建负载均衡客户端
        LoadBalancerClient lbClient = new LoadBalancerClient(loadBalancer, serverList, httpClient);
        
        // 测试负载均衡请求
        HttpResponse<String> response = lbClient.get("test-service", "/get", String.class);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
    }
    
    @Test
    @DisplayName("不同负载均衡算法测试")
    void testDifferentLoadBalancingAlgorithms() {
        Server server1 = new Server("server1", "httpbin.org", 443, "https", "zone1");
        Server server2 = new Server("server2", "httpbin.org", 443, "https", "zone1");
        
        StaticServerList serverList = new StaticServerList();
        serverList.addServer("test-service", server1);
        serverList.addServer("test-service", server2);
        
        // 测试不同算法
        LoadBalancer[] algorithms = {
            new RoundRobinLoadBalancer(),
            new RandomLoadBalancer(),
            new LeastConnectionsLoadBalancer()
        };
        
        for (LoadBalancer algorithm : algorithms) {
            HttpClient httpClient = new SimpleHttpClient();
            LoadBalancerClient lbClient = new LoadBalancerClient(algorithm, serverList, httpClient);
            
            HttpResponse<String> response = lbClient.get("test-service", "/get", String.class);
            assertNotNull(response);
            assertEquals(200, response.getStatusCode());
        }
    }
}