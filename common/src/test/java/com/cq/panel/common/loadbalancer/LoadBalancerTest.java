package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 负载均衡组件测试类
 */
class LoadBalancerTest {
    
    private List<Server> servers;
    private StaticServerList serverList;
    
    @BeforeEach
    void setUp() {
        Server server1 = new Server("server1", "192.168.1.101", 8080, "http", "zone1");
        Server server2 = new Server("server2", "192.168.1.102", 8080, "http", "zone1");
        Server server3 = new Server("server3", "192.168.1.103", 8080, "http", "zone2");
        
        servers = Arrays.asList(server1, server2, server3);
        serverList = new StaticServerList();
        serverList.addServers("test-service", servers);
    }
    
    @Test
    void testRoundRobinLoadBalancer() {
        LoadBalancer loadBalancer = new RoundRobinLoadBalancer();
        
        // 测试轮询选择
        Server first = loadBalancer.choose(servers);
        Server second = loadBalancer.choose(servers);
        Server third = loadBalancer.choose(servers);
        Server fourth = loadBalancer.choose(servers);
        
        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);
        assertNotNull(fourth);
        
        // 验证轮询顺序
        assertEquals(first, fourth); // 第四轮应该和第一轮相同
    }
    
    @Test
    void testRandomLoadBalancer() {
        LoadBalancer loadBalancer = new RandomLoadBalancer();
        
        // 多次选择，验证随机性（虽然不能完全验证，但可以验证功能正常）
        for (int i = 0; i < 10; i++) {
            Server server = loadBalancer.choose(servers);
            assertNotNull(server);
            assertTrue(servers.contains(server));
        }
    }
    
    @Test
    void testLeastConnectionsLoadBalancer() {
        LoadBalancer loadBalancer = new LeastConnectionsLoadBalancer();
        
        // 设置不同的连接数
        servers.get(0).setConcurrentRequests(5);
        servers.get(1).setConcurrentRequests(3);
        servers.get(2).setConcurrentRequests(1);
        
        Server selected = loadBalancer.choose(servers);
        assertNotNull(selected);
        assertEquals(servers.get(2), selected); // 应该选择连接数最少的服务器
    }
    
    @Test
    void testConsistentHashLoadBalancer() {
        LoadBalancer loadBalancer = new ConsistentHashLoadBalancer();
        
        String key1 = "user123";
        String key2 = "user456";
        
        // 相同key应该选择相同的服务器
        Server server1 = loadBalancer.choose(servers, key1);
        Server server2 = loadBalancer.choose(servers, key1);
        
        assertNotNull(server1);
        assertNotNull(server2);
        assertEquals(server1, server2);
        
        // 不同key可能选择不同的服务器
        Server server3 = loadBalancer.choose(servers, key2);
        assertNotNull(server3);
    }
    
    @Test
    void testZoneAwareLoadBalancer() {
        LoadBalancer loadBalancer = new ZoneAwareLoadBalancer("zone1");
        
        // 应该优先选择zone1的服务器
        Server selected = loadBalancer.choose(servers);
        assertNotNull(selected);
        assertEquals("zone1", selected.getZone());
    }
    
    @Test
    void testLoadBalancerFactory() {
        // 测试工厂创建各种负载均衡器
        LoadBalancer roundRobin = LoadBalancerFactory.createLoadBalancer("roundRobin");
        assertInstanceOf(RoundRobinLoadBalancer.class, roundRobin);
        
        LoadBalancer random = LoadBalancerFactory.createLoadBalancer("random");
        assertInstanceOf(RandomLoadBalancer.class, random);
        
        LoadBalancer leastConnections = LoadBalancerFactory.createLoadBalancer("leastConnections");
        assertInstanceOf(LeastConnectionsLoadBalancer.class, leastConnections);
        
        // 测试默认算法
        LoadBalancer defaultLB = LoadBalancerFactory.createLoadBalancer("unknown");
        assertInstanceOf(RoundRobinLoadBalancer.class, defaultLB);
    }
    
    @Test
    void testStaticServerList() {
        // 测试服务器列表功能
        List<Server> retrievedServers = serverList.getServers("test-service");
        assertEquals(3, retrievedServers.size());
        
        // 测试添加服务器
        Server newServer = new Server("server4", "192.168.1.104", 8080, "http", "zone2");
        serverList.addServer("test-service", newServer);
        
        retrievedServers = serverList.getServers("test-service");
        assertEquals(4, retrievedServers.size());
        
        // 测试移除服务器
        serverList.removeServer("test-service", newServer);
        retrievedServers = serverList.getServers("test-service");
        assertEquals(3, retrievedServers.size());
    }
    
    @Test
    void testLoadBalancerManager() {
        LoadBalancerManager manager = LoadBalancerManager.createDefault();
        
        // 测试获取客户端
        LoadBalancerClient client1 = manager.getClient("service1", serverList);
        LoadBalancerClient client2 = manager.getClient("service1", serverList);
        
        assertNotNull(client1);
        assertNotNull(client2);
        assertEquals(client1, client2); // 应该返回相同的客户端实例
        
        // 测试不同算法创建不同客户端
        LoadBalancerClient client3 = manager.getClient("service1", serverList, LoadBalancerAlgorithm.RANDOM);
        assertNotEquals(client1, client3);
        
        // 测试移除客户端
        manager.removeClient("service1");
        LoadBalancerClient client4 = manager.getClient("service1", serverList);
        assertNotEquals(client1, client4); // 应该是新的实例
    }
    
    @Test
    void testServerAliveStatus() {
        LoadBalancer loadBalancer = new RoundRobinLoadBalancer();
        
        // 设置一个服务器为不可用
        servers.getFirst().setAlive(false);
        
        // 应该只从可用的服务器中选择
        for (int i = 0; i < 5; i++) {
            Server selected = loadBalancer.choose(servers);
            assertNotNull(selected);
            assertTrue(selected.isAlive());
            assertNotEquals(servers.getFirst(), selected);
        }
    }
    
    @Test
    void testNotifyServerStatusRoundRobin() {
        LoadBalancer loadBalancer = new RoundRobinLoadBalancer();
        
        Server server1 = servers.getFirst();
        
        // 当前实现中，notifyServerStatus是空方法，不会影响服务器选择
        // 测试调用不会抛出异常
        assertDoesNotThrow(() -> {
            loadBalancer.notifyServerStatus(server1, ServerStatus.DOWN);
        });
        
        // 服务器仍然可以被选择，因为状态通知没有实际效果
        Server selected = loadBalancer.choose(servers);
        assertNotNull(selected);
        assertTrue(selected.isAlive());
    }
    
    @Test
    void testNotifyServerStatusRandom() {
        LoadBalancer loadBalancer = new RandomLoadBalancer();
        
        Server server1 = servers.getFirst();
        
        // 测试调用不会抛出异常
        assertDoesNotThrow(() -> {
            loadBalancer.notifyServerStatus(server1, ServerStatus.DOWN);
        });
        
        // 服务器仍然可以被选择
        Server selected = loadBalancer.choose(servers);
        assertNotNull(selected);
        assertTrue(selected.isAlive());
    }
    
    @Test
    void testNotifyServerStatusLeastConnections() {
        LoadBalancer loadBalancer = new LeastConnectionsLoadBalancer();
        
        Server server1 = servers.get(0);
        Server server2 = servers.get(1);
        Server server3 = servers.get(2);
        
        // 设置连接数
        server1.setConcurrentRequests(5);
        server2.setConcurrentRequests(3);
        server3.setConcurrentRequests(1);
        
        // 正常情况下应该选择连接数最少的服务器3
        Server selected = loadBalancer.choose(servers);
        assertEquals(server3, selected);
        
        // 通知服务器3下线（当前实现不会影响选择）
        loadBalancer.notifyServerStatus(server3, ServerStatus.DOWN);
        
        // 仍然选择服务器3，因为状态通知没有实际效果
        selected = loadBalancer.choose(servers);
        assertEquals(server3, selected);
    }
    
    @Test
    void testNotifyServerStatusConsistentHash() {
        LoadBalancer loadBalancer = new ConsistentHashLoadBalancer();
        
        Server server1 = servers.getFirst();
        String key = "test-key";
        
        // 记录初始选择的服务器
        Server initialServer = loadBalancer.choose(servers, key);
        assertNotNull(initialServer);
        
        // 通知服务器状态变化（当前实现不会影响选择）
        loadBalancer.notifyServerStatus(server1, ServerStatus.DOWN);
        
        // 仍然选择相同的服务器
        Server newServer = loadBalancer.choose(servers, key);
        assertEquals(initialServer, newServer);
    }
    
    @Test
    void testNotifyServerStatusZoneAware() {
        LoadBalancer loadBalancer = new ZoneAwareLoadBalancer("zone1");
        
        Server server1 = servers.getFirst(); // zone1
        
        // 正常情况下应该优先选择zone1的服务器
        Server selected = loadBalancer.choose(servers);
        assertNotNull(selected);
        assertEquals("zone1", selected.getZone());
        
        // 通知服务器状态变化（当前实现不会影响选择）
        loadBalancer.notifyServerStatus(server1, ServerStatus.DOWN);
        
        // 仍然选择zone1的服务器
        selected = loadBalancer.choose(servers);
        assertEquals("zone1", selected.getZone());
    }
    
    @Test
    void testNotifyServerStatusUnknownStatus() {
        LoadBalancer loadBalancer = new RoundRobinLoadBalancer();
        
        Server server1 = servers.getFirst();
        
        // 通知服务器状态为UNKNOWN（当前实现不会影响选择）
        loadBalancer.notifyServerStatus(server1, ServerStatus.UNKNOWN);
        
        // 服务器仍然可以被选择
        Server selected = loadBalancer.choose(servers);
        assertNotNull(selected);
        assertTrue(selected.isAlive());
    }
    
    @Test
    void testNotifyServerStatusStoppingStatus() {
        LoadBalancer loadBalancer = new RandomLoadBalancer();
        
        Server server1 = servers.getFirst();
        
        // 通知服务器状态为STOPPING（当前实现不会影响选择）
        loadBalancer.notifyServerStatus(server1, ServerStatus.STOPPING);
        
        // 服务器仍然可以被选择
        Server selected = loadBalancer.choose(servers);
        assertNotNull(selected);
        assertTrue(selected.isAlive());
    }
    
    @Test
    void testNotifyServerStatusNullServer() {
        LoadBalancer loadBalancer = new RoundRobinLoadBalancer();
        
        // 测试传入null服务器
        assertDoesNotThrow(() -> {
            loadBalancer.notifyServerStatus(null, ServerStatus.UP);
        });
        
        // 负载均衡器应该继续正常工作
        Server selected = loadBalancer.choose(servers);
        assertNotNull(selected);
    }
    
    @Test
    void testNotifyServerStatusNullStatus() {
        LoadBalancer loadBalancer = new RoundRobinLoadBalancer();
        Server server1 = servers.getFirst();
        
        // 测试传入null状态
        assertDoesNotThrow(() -> {
            loadBalancer.notifyServerStatus(server1, null);
        });
        
        // 负载均衡器应该继续正常工作
        Server selected = loadBalancer.choose(servers);
        assertNotNull(selected);
    }
    
    @Test
    void testNotifyServerStatusMultipleCalls() {
        LoadBalancer loadBalancer = new RoundRobinLoadBalancer();
        Server server1 = servers.getFirst();
        
        // 多次调用状态通知（当前实现不会影响选择）
        loadBalancer.notifyServerStatus(server1, ServerStatus.DOWN);
        loadBalancer.notifyServerStatus(server1, ServerStatus.DOWN); // 重复调用
        loadBalancer.notifyServerStatus(server1, ServerStatus.UP);
        loadBalancer.notifyServerStatus(server1, ServerStatus.DOWN);
        
        // 服务器仍然可以被选择
        Server selected = loadBalancer.choose(servers);
        assertNotNull(selected);
        assertTrue(selected.isAlive());
    }
}