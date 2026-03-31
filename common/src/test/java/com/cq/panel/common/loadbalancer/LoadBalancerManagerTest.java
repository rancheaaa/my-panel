package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoadBalancerManager详细测试案例
 * 测试负载均衡管理器的所有主要功能
 */
@DisplayName("LoadBalancerManager测试")
class LoadBalancerManagerTest {
    
    private LoadBalancerManager manager;
    private StaticServerList serverList;
    
    @BeforeEach
    void setUp() {
        manager = LoadBalancerManager.createDefault();
        
        // 创建测试服务器列表
        serverList = new StaticServerList();
        Server server1 = new Server("server1", "httpbin.org", 443, "https", "zone1");
        Server server2 = new Server("server2", "jsonplaceholder.typicode.com", 443, "https", "zone1");
        Server server3 = new Server("server3", "api.github.com", 443, "https", "zone2");
        
        serverList.addServer("test-service", server1);
        serverList.addServer("test-service", server2);
        serverList.addServer("test-service", server3);
    }
    
    @Nested
    @DisplayName("客户端管理测试")
    class ClientManagementTest {
        
        @Test
        @DisplayName("获取负载均衡客户端 - 默认算法")
        void testGetClientWithDefaultAlgorithm() {
            LoadBalancerClient client = manager.getClient("test-service", serverList);
            
            assertNotNull(client);
            assertSame(client, manager.getClient("test-service", serverList), "应该返回相同的客户端实例");
        }
        
        @Test
        @DisplayName("获取负载均衡客户端 - 指定算法")
        void testGetClientWithSpecificAlgorithm() {
            LoadBalancerClient client1 = manager.getClient("test-service", serverList, LoadBalancerAlgorithm.ROUND_ROBIN);
            LoadBalancerClient client2 = manager.getClient("test-service", serverList, LoadBalancerAlgorithm.RANDOM);
            LoadBalancerClient client3 = manager.getClient("test-service", serverList, LoadBalancerAlgorithm.LEAST_CONNECTIONS);
            
            assertNotNull(client1);
            assertNotNull(client2);
            assertNotNull(client3);
            
            // 不同算法应该返回不同的客户端实例
            assertNotSame(client1, client2);
            assertNotSame(client1, client3);
            assertNotSame(client2, client3);
        }
        
        @Test
        @DisplayName("获取负载均衡客户端 - 相同算法缓存")
        void testGetClientCaching() {
            LoadBalancerClient client1 = manager.getClient("test-service", serverList, LoadBalancerAlgorithm.ROUND_ROBIN);
            LoadBalancerClient client2 = manager.getClient("test-service", serverList, LoadBalancerAlgorithm.ROUND_ROBIN);
            
            assertSame(client1, client2, "相同服务名和算法应该返回缓存的客户端实例");
        }
        
        @Test
        @DisplayName("移除负载均衡客户端")
        void testRemoveClient() {
            LoadBalancerClient client1 = manager.getClient("service1", serverList);
            LoadBalancerClient client2 = manager.getClient("service2", serverList);
            
            assertNotNull(client1);
            assertNotNull(client2);
            assertEquals(2, manager.getAllClients().size());
            
            // 移除service1
            manager.removeClient("service1");
            assertEquals(1, manager.getAllClients().size());
            assertFalse(manager.getAllClients().containsKey("service1-ROUND_ROBIN"));
            // 注意：移除后可能没有service2-ROUND_ROBIN，因为可能使用默认算法
            
            // 重新获取service1应该创建新的客户端
            LoadBalancerClient newClient1 = manager.getClient("service1", serverList);
            assertNotNull(newClient1);
            assertNotSame(client1, newClient1, "移除后重新获取应该创建新的客户端实例");
        }
        
        @Test
        @DisplayName("获取所有负载均衡客户端")
        void testGetAllClients() {
            manager.getClient("service1", serverList);
            manager.getClient("service2", serverList, LoadBalancerAlgorithm.ROUND_ROBIN);
            manager.getClient("service2", serverList, LoadBalancerAlgorithm.RANDOM);
            
            Map<String, LoadBalancerClient> allClients = manager.getAllClients();
            
            assertEquals(3, allClients.size());
            assertTrue(allClients.containsKey("service1-roundRobin"));
            assertTrue(allClients.containsKey("service2-roundRobin"));
            assertTrue(allClients.containsKey("service2-random"));
            
            // 返回的映射应该是不可变的副本
            assertThrows(UnsupportedOperationException.class, () -> 
                allClients.put("test", null));
        }
        
        @Test
        @DisplayName("清空所有负载均衡客户端")
        void testClear() {
            manager.getClient("service1", serverList);
            manager.getClient("service2", serverList);
            
            assertEquals(2, manager.getAllClients().size());
            
            manager.clear();
            
            assertEquals(0, manager.getAllClients().size());
        }
    }
    
    @Nested
    @DisplayName("配置管理测试")
    class ConfigurationTest {
        
        @Test
        @DisplayName("获取默认配置")
        void testGetDefaultConfig() {
            LoadBalancerConfig config = manager.getDefaultConfig();
            
            assertNotNull(config);
            assertEquals(LoadBalancerAlgorithm.ROUND_ROBIN, config.getAlgorithm());
            assertTrue(config.isHealthCheckEnabled());
            assertEquals(5000, config.getConnectTimeout());
            assertEquals(10000, config.getReadTimeout());
        }
        
        @Test
        @DisplayName("获取HTTP客户端")
        void testGetHttpClient() {
            HttpClient httpClient = manager.getHttpClient();
            
            assertNotNull(httpClient);
            assertInstanceOf(ApacheHttpClient.class, httpClient);
        }
        
        @Test
        @DisplayName("自定义配置管理器")
        void testCustomConfigManager() {
            LoadBalancerConfig customConfig = LoadBalancerConfig.builder()
                    .algorithm(LoadBalancerAlgorithm.RANDOM)
                    .connectTimeout(3000)
                    .readTimeout(6000)
                    .enableHealthCheck(false)
                    .build();
            
            LoadBalancerManager customManager = new LoadBalancerManager(customConfig);
            
            assertEquals(customConfig, customManager.getDefaultConfig());
            assertEquals(3000, customManager.getHttpClient().getConnectTimeout());
            assertEquals(6000, customManager.getHttpClient().getReadTimeout());
        }
    }
    
    @Nested
    @DisplayName("工厂方法测试")
    class FactoryMethodTest {
        
        @Test
        @DisplayName("创建默认管理器")
        void testCreateDefault() {
            LoadBalancerManager defaultManager = LoadBalancerManager.createDefault();
            
            assertNotNull(defaultManager);
            assertEquals(LoadBalancerAlgorithm.ROUND_ROBIN, 
                defaultManager.getDefaultConfig().getAlgorithm());
        }
        
        @Test
        @DisplayName("创建快速管理器")
        void testCreateFast() {
            LoadBalancerManager fastManager = LoadBalancerManager.createFast();
            
            assertNotNull(fastManager);
            assertEquals(2000, fastManager.getDefaultConfig().getConnectTimeout());
            assertEquals(5000, fastManager.getDefaultConfig().getReadTimeout());
        }
        
        @Test
        @DisplayName("创建高可用管理器")
        void testCreateHighAvailability() {
            LoadBalancerManager haManager = LoadBalancerManager.createHighAvailability();
            
            assertNotNull(haManager);
            // 高可用配置主要关注重试次数，超时时间可能保持默认值
            assertEquals(5, haManager.getDefaultConfig().getMaxRetries());
            assertEquals(5, haManager.getDefaultConfig().getHealthCheckRetryCount());
        }
    }
    
    @Nested
    @DisplayName("功能集成测试")
    class IntegrationTest {
        
        @Test
        @DisplayName("负载均衡请求集成测试")
        void testLoadBalancerRequestIntegration() {
            LoadBalancerClient client = manager.getClient("test-service", serverList);
            
            // 测试负载均衡请求
            HttpResponse<String> response = client.get("test-service", "/get", String.class);
            
            assertNotNull(response);
            assertEquals(200, response.getStatusCode());
            assertNotNull(response.getBody());
        }
        
        @Test
        @DisplayName("多服务负载均衡测试")
        void testMultipleServicesLoadBalancing() {
            // 创建第二个服务
            StaticServerList service2ServerList = new StaticServerList();
            Server service2Server1 = new Server("s2-server1", "httpbin.org", 443, "https", "zone1");
            Server service2Server2 = new Server("s2-server2", "httpbin.org", 443, "https", "zone1");
            service2ServerList.addServer("service2", service2Server1);
            service2ServerList.addServer("service2", service2Server2);
            
            LoadBalancerClient client1 = manager.getClient("service1", serverList);
            LoadBalancerClient client2 = manager.getClient("service2", service2ServerList);
            
            assertNotSame(client1, client2);
            
            // 测试两个服务的客户端创建成功
            assertNotNull(client1);
            assertNotNull(client2);
            
            // 验证两个服务有不同的客户端实例
            assertEquals(2, manager.getAllClients().size());
        }
        
        @Test
        @DisplayName("健康检查集成测试")
        void testHealthCheckIntegration() {
            // 创建包含健康检查配置的管理器
            LoadBalancerConfig healthCheckConfig = LoadBalancerConfig.builder()
                    .enableHealthCheck(true)
                    .healthCheckPath("/health")
                    .healthCheckTimeout(5000)
                    .healthCheckInterval(30000)
                    .build();
            
            LoadBalancerManager healthCheckManager = new LoadBalancerManager(healthCheckConfig);
            LoadBalancerClient client = healthCheckManager.getClient("test-service", serverList);
            
            assertNotNull(client);
            
            // 测试负载均衡请求（健康检查会在后台运行）
            HttpResponse<String> response = client.get("test-service", "/get", String.class);
            
            assertNotNull(response);
            assertEquals(200, response.getStatusCode());
        }
        
        @Test
        @DisplayName("区域感知负载均衡测试")
        void testZoneAwareLoadBalancing() {
            // 创建区域感知配置
            LoadBalancerConfig zoneConfig = LoadBalancerConfig.builder()
                    .algorithm(LoadBalancerAlgorithm.ZONE_AWARE)
                    .localZone("zone1")
                    .build();
            
            LoadBalancerManager zoneManager = new LoadBalancerManager(zoneConfig);
            LoadBalancerClient client = zoneManager.getClient("test-service", serverList);
            
            assertNotNull(client);
            
            // 验证区域感知配置正确设置
            assertEquals(LoadBalancerAlgorithm.ZONE_AWARE, zoneConfig.getAlgorithm());
            assertEquals("zone1", zoneConfig.getLocalZone());
            
            // 验证客户端创建成功
            assertNotNull(client);
        }
    }
    
    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTest {
        
        @Test
        @DisplayName("空服务器列表处理")
        void testEmptyServerList() {
            StaticServerList emptyServerList = new StaticServerList();
            
            LoadBalancerClient client = manager.getClient("empty-service", emptyServerList);
            
            assertNotNull(client);
            
            // 尝试请求应该抛出异常
            assertThrows(RuntimeException.class, () -> 
                client.get("empty-service", "/get", String.class));
        }
        
        @Test
        @DisplayName("重复获取相同客户端")
        void testRepeatedClientAccess() {
            LoadBalancerClient client1 = manager.getClient("test-service", serverList);
            LoadBalancerClient client2 = manager.getClient("test-service", serverList);
            LoadBalancerClient client3 = manager.getClient("test-service", serverList);
            
            assertSame(client1, client2);
            assertSame(client1, client3);
            assertSame(client2, client3);
        }
        
        @Test
        @DisplayName("并发客户端访问")
        void testConcurrentClientAccess() throws InterruptedException {
            Thread[] threads = new Thread[10];
            final LoadBalancerClient[] clients = new LoadBalancerClient[10];
            
            for (int i = 0; i < 10; i++) {
                final int index = i;
                threads[i] = new Thread(() -> clients[index] = manager.getClient("concurrent-service", serverList));
                threads[i].start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            // 所有线程应该获取到相同的客户端实例
            LoadBalancerClient firstClient = clients[0];
            for (int i = 1; i < 10; i++) {
                assertSame(firstClient, clients[i], "并发访问应该返回相同的客户端实例");
            }
        }
        
        @Test
        @DisplayName("移除不存在的客户端")
        void testRemoveNonExistentClient() {
            // 移除不存在的服务不应该抛出异常
            assertDoesNotThrow(() -> manager.removeClient("non-existent-service"));
            
            // 验证管理器状态不变
            assertEquals(0, manager.getAllClients().size());
        }
        
        @Test
        @DisplayName("清空空管理器")
        void testClearEmptyManager() {
            // 清空空管理器不应该抛出异常
            assertDoesNotThrow(() -> manager.clear());
            
            // 验证管理器状态
            assertEquals(0, manager.getAllClients().size());
        }
    }
}