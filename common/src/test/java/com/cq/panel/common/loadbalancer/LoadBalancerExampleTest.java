package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 负载均衡组件测试案例
 * 将原来的示例程序拆分为多个独立的测试方法
 */
class LoadBalancerExampleTest {
    
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerExampleTest.class);
    
    private StaticServerList serverList;
    private LoadBalancerManager manager;
    private List<Server> servers;
    
    @BeforeEach
    void setUp() {
        // 创建测试服务器列表
        Server server1 = new Server("server1", "192.168.1.101", 8080, "http", "zone1");
        Server server2 = new Server("server2", "192.168.1.102", 8080, "http", "zone1");
        Server server3 = new Server("server3", "192.168.1.103", 8080, "http", "zone2");
        
        servers = Arrays.asList(server1, server2, server3);
        
        // 创建静态服务器列表
        serverList = new StaticServerList();
        serverList.addServers("user-service", servers);
        
        // 创建负载均衡管理器
        manager = LoadBalancerManager.createDefault();
    }
    
    @Test
    void testServerCreation() {
        logger.info("=== 测试服务器创建 ===");
        
        assertNotNull(serverList);
        assertNotNull(servers);
        assertEquals(3, servers.size());
        
        // 验证服务器属性
        Server server1 = servers.getFirst();
        assertEquals("server1", server1.getId());
        assertEquals("192.168.1.101", server1.getHost());
        assertEquals(8080, server1.getPort());
        assertEquals("http", server1.getScheme());
        assertEquals("zone1", server1.getZone());
        assertEquals("http://192.168.1.101:8080", server1.getUrl());
        assertTrue(server1.isAlive());
        
        logger.info("服务器创建测试通过");
    }
    
    @Test
    void testStaticServerList() {
        logger.info("=== 测试静态服务器列表 ===");
        
        assertNotNull(serverList);
        
        // 验证服务列表
        List<Server> userServiceServers = serverList.getServers("user-service");
        assertNotNull(userServiceServers);
        assertEquals(3, userServiceServers.size());
        
        // 验证服务器顺序
        assertEquals("server1", userServiceServers.get(0).getId());
        assertEquals("server2", userServiceServers.get(1).getId());
        assertEquals("server3", userServiceServers.get(2).getId());
        
        // 测试不存在的服务
        List<Server> unknownService = serverList.getServers("unknown-service");
        assertNotNull(unknownService);
        assertTrue(unknownService.isEmpty());
        
        logger.info("静态服务器列表测试通过");
    }
    
    @Test
    void testRoundRobinLoadBalancer() {
        logger.info("=== 测试轮询负载均衡算法 ===");
        
        LoadBalancerClient client = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.ROUND_ROBIN);
        assertNotNull(client);
        
        LoadBalancer loadBalancer = client.getLoadBalancer();
        assertNotNull(loadBalancer);
        assertEquals("roundRobin", loadBalancer.getName());
        
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
        assertEquals(first, servers.get(0));
        assertEquals(second, servers.get(1));
        assertEquals(third, servers.get(2));
        assertEquals(fourth, servers.get(0)); // 回到第一个
        
        logger.info("轮询负载均衡算法测试通过");
    }
    
    @Test
    void testRandomLoadBalancer() {
        logger.info("=== 测试随机负载均衡算法 ===");
        
        LoadBalancerClient client = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.RANDOM);
        assertNotNull(client);
        
        LoadBalancer loadBalancer = client.getLoadBalancer();
        assertNotNull(loadBalancer);
        assertEquals("random", loadBalancer.getName());
        
        // 测试随机选择（多次测试以确保随机性）
        boolean foundServer1 = false;
        boolean foundServer2 = false;
        boolean foundServer3 = false;
        
        for (int i = 0; i < 20; i++) {
            Server server = loadBalancer.choose(servers);
            assertNotNull(server);
            assertTrue(servers.contains(server));
            
            if (server.getId().equals("server1")) foundServer1 = true;
            if (server.getId().equals("server2")) foundServer2 = true;
            if (server.getId().equals("server3")) foundServer3 = true;
            
            if (foundServer1 && foundServer2 && foundServer3) break;
        }
        
        // 验证所有服务器都被选择过（概率很高）
        assertTrue(foundServer1, "应该选择过server1");
        assertTrue(foundServer2, "应该选择过server2");
        assertTrue(foundServer3, "应该选择过server3");
        
        logger.info("随机负载均衡算法测试通过");
    }
    
    @Test
    void testLeastConnectionsLoadBalancer() {
        logger.info("=== 测试最少连接负载均衡算法 ===");
        
        LoadBalancerClient client = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.LEAST_CONNECTIONS);
        assertNotNull(client);
        
        LoadBalancer loadBalancer = client.getLoadBalancer();
        assertNotNull(loadBalancer);
        assertEquals("leastConnections", loadBalancer.getName());
        
        // 初始状态下，所有服务器连接数都为0，应该选择第一个
        Server first = loadBalancer.choose(servers);
        assertNotNull(first);
        assertEquals("server1", first.getId());
        
        // 模拟增加连接数
        servers.get(0).incrementConcurrentRequests(); // server1: 1 connection
        servers.get(0).incrementConcurrentRequests(); // server1: 2 connections
        servers.get(1).incrementConcurrentRequests(); // server2: 1 connection
        
        // 现在应该选择server3（连接数最少，为0）
        Server second = loadBalancer.choose(servers);
        assertNotNull(second);
        assertEquals("server3", second.getId());
        
        // 增加server3的连接数
        servers.get(2).incrementConcurrentRequests(); // server3: 1 connection
        
        // 现在应该选择server2（连接数最少，为1）
        Server third = loadBalancer.choose(servers);
        assertNotNull(third);
        assertEquals("server2", third.getId());
        
        logger.info("最少连接负载均衡算法测试通过");
    }
    
    @Test
    void testConsistentHashLoadBalancer() {
        logger.info("=== 测试一致性哈希负载均衡算法 ===");
        
        LoadBalancerClient client = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.CONSISTENT_HASH);
        assertNotNull(client);
        
        LoadBalancer loadBalancer = client.getLoadBalancer();
        assertNotNull(loadBalancer);
        assertEquals("consistentHash", loadBalancer.getName());
        
        // 测试一致性哈希：相同key应该选择相同服务器
        String key1 = "user123";
        String key2 = "user456";
        String key3 = "user789";
        
        Server server1 = loadBalancer.choose(servers, key1);
        Server server2 = loadBalancer.choose(servers, key1);
        Server server3 = loadBalancer.choose(servers, key2);
        Server server4 = loadBalancer.choose(servers, key2);
        Server server5 = loadBalancer.choose(servers, key3);
        
        assertNotNull(server1);
        assertNotNull(server2);
        assertNotNull(server3);
        assertNotNull(server4);
        assertNotNull(server5);
        
        // 验证一致性：相同key选择相同服务器
        assertEquals(server1, server2, "相同key应该选择相同服务器");
        assertEquals(server3, server4, "相同key应该选择相同服务器");
        
        // 验证不同key可能选择不同服务器（但允许相同，因为这是可能的）
        // 我们只验证算法的一致性，不强制要求不同key必须选择不同服务器
        logger.info("Key '{}' 选择服务器: {}", key1, server1.getId());
        logger.info("Key '{}' 选择服务器: {}", key2, server3.getId());
        logger.info("Key '{}' 选择服务器: {}", key3, server5.getId());
        
        // 验证算法能够正常工作（不强制要求所有服务器都被选择）
        // 一致性哈希的主要特性是相同key选择相同服务器，我们已经验证了这一点
        
        logger.info("一致性哈希负载均衡算法测试通过");
    }
    
    @Test
    void testWeightedRoundRobinLoadBalancer() {
        logger.info("=== 测试加权轮询负载均衡算法 ===");
        
        LoadBalancerClient client = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.WEIGHTED_ROUND_ROBIN);
        assertNotNull(client);
        
        LoadBalancer loadBalancer = client.getLoadBalancer();
        assertNotNull(loadBalancer);
        assertEquals("weightedRoundRobin", loadBalancer.getName());
        
        // 测试加权轮询选择
        Server first = loadBalancer.choose(servers);
        Server second = loadBalancer.choose(servers);
        Server third = loadBalancer.choose(servers);
        Server fourth = loadBalancer.choose(servers);
        
        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);
        assertNotNull(fourth);
        
        // 验证算法正常工作（不验证具体顺序，因为基于响应时间权重）
        assertTrue(servers.contains(first));
        assertTrue(servers.contains(second));
        assertTrue(servers.contains(third));
        assertTrue(servers.contains(fourth));
        
        // 验证算法能够连续多次选择服务器
        for (int i = 0; i < 10; i++) {
            Server server = loadBalancer.choose(servers);
            assertNotNull(server);
            assertTrue(servers.contains(server));
        }
        
        logger.info("加权轮询负载均衡算法测试通过");
    }
    
    @Test
    void testWeightedRandomLoadBalancer() {
        logger.info("=== 测试加权随机负载均衡算法 ===");
        
        LoadBalancerClient client = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.WEIGHTED_RANDOM);
        assertNotNull(client);
        
        LoadBalancer loadBalancer = client.getLoadBalancer();
        assertNotNull(loadBalancer);
        assertEquals("weightedRandom", loadBalancer.getName());
        
        // 测试加权随机选择（多次测试以确保随机性）
        boolean foundServer1 = false;
        boolean foundServer2 = false;
        boolean foundServer3 = false;
        
        for (int i = 0; i < 20; i++) {
            Server server = loadBalancer.choose(servers);
            assertNotNull(server);
            assertTrue(servers.contains(server));
            
            if (server.getId().equals("server1")) foundServer1 = true;
            if (server.getId().equals("server2")) foundServer2 = true;
            if (server.getId().equals("server3")) foundServer3 = true;
            
            if (foundServer1 && foundServer2 && foundServer3) break;
        }
        
        // 验证所有服务器都被选择过（概率很高）
        assertTrue(foundServer1, "应该选择过server1");
        assertTrue(foundServer2, "应该选择过server2");
        assertTrue(foundServer3, "应该选择过server3");
        
        logger.info("加权随机负载均衡算法测试通过");
    }
    
    @Test
    void testFastestResponseLoadBalancer() {
        logger.info("=== 测试最快响应负载均衡算法 ===");
        
        LoadBalancerClient client = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.FASTEST_RESPONSE);
        assertNotNull(client);
        
        LoadBalancer loadBalancer = client.getLoadBalancer();
        assertNotNull(loadBalancer);
        assertEquals("fastestResponse", loadBalancer.getName());
        
        // 初始状态下，所有服务器响应时间都为0，应该选择第一个
        Server first = loadBalancer.choose(servers);
        assertNotNull(first);
        assertEquals("server1", first.getId());
        
        // 模拟设置响应时间
        servers.get(0).setResponseTime(100); // server1: 100ms
        servers.get(1).setResponseTime(50);  // server2: 50ms
        servers.get(2).setResponseTime(200); // server3: 200ms
        
        // 现在应该选择server2（响应时间最短，为50ms）
        Server second = loadBalancer.choose(servers);
        assertNotNull(second);
        assertEquals("server2", second.getId());
        
        // 修改响应时间
        servers.get(1).setResponseTime(150); // server2: 150ms
        servers.get(2).setResponseTime(30);  // server3: 30ms
        
        // 现在应该选择server3（响应时间最短，为30ms）
        Server third = loadBalancer.choose(servers);
        assertNotNull(third);
        assertEquals("server3", third.getId());
        
        logger.info("最快响应负载均衡算法测试通过");
    }
    
    @Test
    void testZoneAwareLoadBalancer() {
        logger.info("=== 测试区域感知负载均衡算法 ===");
        
        LoadBalancerClient client = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.ZONE_AWARE);
        assertNotNull(client);
        
        LoadBalancer loadBalancer = client.getLoadBalancer();
        assertNotNull(loadBalancer);
        assertEquals("zoneAware", loadBalancer.getName());
        
        // 测试区域感知选择
        Server server = loadBalancer.choose(servers);
        assertNotNull(server);
        
        // 验证选择的服务器在可用区域内
        assertTrue(server.isAlive());
        assertTrue(servers.contains(server));
        
        // 测试多次选择，确保算法正常工作
        for (int i = 0; i < 5; i++) {
            Server selected = loadBalancer.choose(servers);
            assertNotNull(selected);
            assertTrue(selected.isAlive());
            assertTrue(servers.contains(selected));
        }
        
        logger.info("区域感知负载均衡算法测试通过");
    }
    
    @Test
    void testLoadBalancerManager() {
        logger.info("=== 测试负载均衡管理器 ===");
        
        assertNotNull(manager);
        
        // 测试获取不同算法的客户端
        LoadBalancerClient roundRobinClient = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.ROUND_ROBIN);
        LoadBalancerClient randomClient = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.RANDOM);
        LoadBalancerClient leastConnectionsClient = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.LEAST_CONNECTIONS);
        LoadBalancerClient consistentHashClient = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.CONSISTENT_HASH);
        LoadBalancerClient weightedRoundRobinClient = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.WEIGHTED_ROUND_ROBIN);
        LoadBalancerClient weightedRandomClient = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.WEIGHTED_RANDOM);
        LoadBalancerClient fastestResponseClient = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.FASTEST_RESPONSE);
        LoadBalancerClient zoneAwareClient = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.ZONE_AWARE);
        
        assertNotNull(roundRobinClient);
        assertNotNull(randomClient);
        assertNotNull(leastConnectionsClient);
        assertNotNull(consistentHashClient);
        assertNotNull(weightedRoundRobinClient);
        assertNotNull(weightedRandomClient);
        assertNotNull(fastestResponseClient);
        assertNotNull(zoneAwareClient);
        
        // 验证不同算法使用不同的负载均衡器
        assertNotEquals(roundRobinClient.getLoadBalancer(), randomClient.getLoadBalancer());
        assertNotEquals(roundRobinClient.getLoadBalancer(), leastConnectionsClient.getLoadBalancer());
        assertNotEquals(roundRobinClient.getLoadBalancer(), consistentHashClient.getLoadBalancer());
        
        logger.info("负载均衡管理器测试通过");
    }
    
    @Test
    void testServerHealth() {
        logger.info("=== 测试服务器健康状态 ===");
        
        Server server = servers.getFirst();
        assertTrue(server.isAlive(), "服务器初始状态应该是健康的");
        logger.info("last access time {}", server.getLastAccessTime());
        // 模拟服务器故障
        server.setAlive(false);
        assertFalse(server.isAlive(), "标记为不健康后应该返回false");
        
        // 模拟服务器恢复
        server.setAlive(true);
        assertTrue(server.isAlive(), "标记为健康后应该返回true");
        
        // 测试连接数管理
        assertEquals(0, server.getConcurrentRequests(), "初始连接数应该为0");
        
        server.incrementConcurrentRequests();
        assertEquals(1, server.getConcurrentRequests(), "增加连接后应该为1");
        
        server.decrementConcurrentRequests();
        assertEquals(0, server.getConcurrentRequests(), "减少连接后应该为0");
        
        logger.info("服务器健康状态测试通过");
        logger.info("last access time {}", server.getLastAccessTime());
    }
    
    @Test
    void testLoadBalancerClient() {
        logger.info("=== 测试负载均衡客户端 ===");
        
        LoadBalancerClient client = manager.getClient("user-service", serverList, LoadBalancerAlgorithm.ROUND_ROBIN);
        assertNotNull(client);
        
        // 验证客户端属性
        assertNotNull(client.getServerList());
        assertNotNull(client.getLoadBalancer());
        assertNotNull(client.getHttpClient());
        
        // 验证服务器列表
        List<Server> clientServers = client.getServerList().getServers("user-service");
        assertNotNull(clientServers);
        assertEquals(3, clientServers.size());
        
        logger.info("负载均衡客户端测试通过");
    }
    
    @Test
    void testEmptyServerList() {
        logger.info("=== 测试空服务器列表 ===");
        
        // 创建空服务器列表
        StaticServerList emptyServerList = new StaticServerList();
        emptyServerList.addServers("empty-service", List.of());
        
        LoadBalancerClient client = manager.getClient("empty-service", emptyServerList, LoadBalancerAlgorithm.ROUND_ROBIN);
        assertNotNull(client);
        
        // 从空列表中选择服务器应该返回null
        Server server = client.getLoadBalancer().choose(emptyServerList.getServers("empty-service"));
        assertNull(server, "空服务器列表应该返回null");
        
        logger.info("空服务器列表测试通过");
    }
    
    @Test
    void testSingleServer() {
        logger.info("=== 测试单服务器场景 ===");
        
        // 创建单服务器列表
        StaticServerList singleServerList = new StaticServerList();
        Server singleServer = new Server("single", "192.168.1.100", 8080, "http", "zone1");
        singleServerList.addServers("single-service", List.of(singleServer));
        
        LoadBalancerClient client = manager.getClient("single-service", singleServerList, LoadBalancerAlgorithm.ROUND_ROBIN);
        assertNotNull(client);
        
        // 多次选择应该都返回同一个服务器
        for (int i = 0; i < 5; i++) {
            Server server = client.getLoadBalancer().choose(singleServerList.getServers("single-service"));
            assertNotNull(server);
            assertEquals("single", server.getId());
        }
        
        logger.info("单服务器场景测试通过");
    }
}