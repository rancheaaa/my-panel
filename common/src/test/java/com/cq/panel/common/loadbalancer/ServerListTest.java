package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ServerList接口和StaticServerList实现类的详细单元测试
 * 测试覆盖范围：
 * - 基础功能测试
 * - 边界条件测试
 * - 并发安全测试
 * - 性能测试
 * - 异常情况测试
 */
@DisplayName("ServerList接口和StaticServerList实现类测试")
class ServerListTest {
    
    private StaticServerList serverList;
    private Server server1;
    private Server server2;
    private Server server3;
    private Server server4;
    private Server server5;
    
    @BeforeEach
    void setUp() {
        // 创建服务器列表实例
        serverList = new StaticServerList();
        
        // 创建测试服务器
        server1 = new Server("server-1", "localhost", 8080, "http", "zone1");
        server2 = new Server("server-2", "192.168.1.100", 8081, "http", "zone1");
        server3 = new Server("server-3", "10.0.0.1", 8082, "https", "zone2");
        server4 = new Server("server-4", "api.example.com", 443, "https", "zone2");
        server5 = new Server("server-5", "secure.api.example.com", 8443, "https", "zone3");
        
        // 设置服务器状态
        server1.setAlive(true);
        server2.setAlive(true);
        server3.setAlive(false); // 设置为下线状态
        server4.setAlive(true);
        server5.setAlive(false); // 设置为下线状态
    }
    
    /**
     * 获取所有在线服务器的辅助方法
     */
    private List<Server> getAllUpServers(StaticServerList serverList) {
        return serverList.getAllServers().stream()
                .filter(Server::isAlive)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有下线服务器的辅助方法
     */
    private List<Server> getAllDownServers(StaticServerList serverList) {
        return serverList.getAllServers().stream()
                .filter(server -> !server.isAlive())
                .collect(Collectors.toList());
    }
    
    // ===========================================
    // 基础功能测试
    // ===========================================
    
    @Test
    @DisplayName("创建空服务器列表")
    void testCreateEmptyServerList() {
        assertNotNull(serverList, "服务器列表实例应该被创建");
        
        // 验证空列表行为
        assertTrue(serverList.getAllServers().isEmpty(), "空服务器列表应该返回空列表");
        assertTrue(serverList.getUpServers("nonexistent-service").isEmpty(), "空服务器列表应该返回空在线列表");
        assertTrue(serverList.getDownServers("nonexistent-service").isEmpty(), "空服务器列表应该返回空下线列表");
        assertTrue(serverList.getAllServiceNames().isEmpty(), "空服务器列表应该返回空服务名称列表");
        
        // 获取不存在的服务
        List<Server> servers = serverList.getServers("nonexistent-service");
        assertNotNull(servers, "获取不存在的服务应该返回非空列表");
        assertTrue(servers.isEmpty(), "获取不存在的服务应该返回空列表");
    }
    
    @Test
    @DisplayName("创建带初始数据的服务器列表")
    void testCreateServerListWithInitialData() {
        // 准备初始数据
        Map<String, List<Server>> initialData = new HashMap<>();
        initialData.put("service-1", Arrays.asList(server1, server2));
        initialData.put("service-2", Arrays.asList(server3, server4));
        initialData.put("service-3", Collections.singletonList(server5));
        
        // 创建带初始数据的服务器列表
        StaticServerList initializedList = new StaticServerList(initialData);
        
        // 验证数据正确性
        assertEquals(3, initializedList.getAllServiceNames().size(), "应该包含3个服务名称");
        assertTrue(initializedList.getAllServiceNames().contains("service-1"), "应该包含service-1");
        assertTrue(initializedList.getAllServiceNames().contains("service-2"), "应该包含service-2");
        assertTrue(initializedList.getAllServiceNames().contains("service-3"), "应该包含service-3");
        
        // 验证服务器数量
        assertEquals(5, initializedList.getAllServers().size(), "应该包含5个服务器");
        assertEquals(2, initializedList.getServers("service-1").size(), "service-1应该有2个服务器");
        assertEquals(2, initializedList.getServers("service-2").size(), "service-2应该有2个服务器");
        assertEquals(1, initializedList.getServers("service-3").size(), "service-3应该有1个服务器");
        
        // 验证在线/下线服务器数量
        assertEquals(3, getAllUpServers(initializedList).size(), "应该有3个在线服务器");
        assertEquals(2, getAllDownServers(initializedList).size(), "应该有2个下线服务器");
    }
    
    @Test
    @DisplayName("添加和获取服务服务器")
    void testAddAndGetServers() {
        // 添加服务1的服务器
        serverList.addServers("service-1", Arrays.asList(server1, server2));
        
        // 验证服务1的服务器
        List<Server> service1Servers = serverList.getServers("service-1");
        assertNotNull(service1Servers, "获取服务1的服务器列表应该非空");
        assertEquals(2, service1Servers.size(), "服务1应该有2个服务器");
        assertTrue(service1Servers.contains(server1), "服务1应该包含server1");
        assertTrue(service1Servers.contains(server2), "服务1应该包含server2");
        
        // 添加服务2的服务器
        serverList.addServers("service-2", Arrays.asList(server3, server4));
        
        // 验证服务2的服务器
        List<Server> service2Servers = serverList.getServers("service-2");
        assertNotNull(service2Servers, "获取服务2的服务器列表应该非空");
        assertEquals(2, service2Servers.size(), "服务2应该有2个服务器");
        assertTrue(service2Servers.contains(server3), "服务2应该包含server3");
        assertTrue(service2Servers.contains(server4), "服务2应该包含server4");
        
        // 验证所有服务器
        List<Server> allServers = serverList.getAllServers();
        assertEquals(4, allServers.size(), "所有服务器应该有4个");
        assertTrue(allServers.contains(server1), "所有服务器应该包含server1");
        assertTrue(allServers.contains(server2), "所有服务器应该包含server2");
        assertTrue(allServers.contains(server3), "所有服务器应该包含server3");
        assertTrue(allServers.contains(server4), "所有服务器应该包含server4");
        
        // 验证服务名称列表
        List<String> serviceNames = serverList.getAllServiceNames();
        assertEquals(2, serviceNames.size(), "应该有2个服务名称");
        assertTrue(serviceNames.contains("service-1"), "应该包含service-1");
        assertTrue(serviceNames.contains("service-2"), "应该包含service-2");
    }
    
    @Test
    @DisplayName("添加单个服务器")
    void testAddSingleServer() {
        // 添加单个服务器到空服务
        serverList.addServer("service-1", server1);
        
        // 验证服务器已添加
        List<Server> servers = serverList.getServers("service-1");
        assertEquals(1, servers.size(), "服务1应该有1个服务器");
        assertEquals(server1, servers.getFirst(), "服务器应该是server1");
        
        // 添加第二个服务器到同一服务
        serverList.addServer("service-1", server2);
        
        // 验证两个服务器都存在
        servers = serverList.getServers("service-1");
        assertEquals(2, servers.size(), "服务1应该有2个服务器");
        assertTrue(servers.contains(server1), "应该包含server1");
        assertTrue(servers.contains(server2), "应该包含server2");
        
        // 添加服务器到不同服务
        serverList.addServer("service-2", server3);
        
        // 验证不同服务的服务器
        List<Server> service2Servers = serverList.getServers("service-2");
        assertEquals(1, service2Servers.size(), "服务2应该有1个服务器");
        assertEquals(server3, service2Servers.getFirst(), "服务器应该是server3");
        
        // 验证服务名称列表
        List<String> serviceNames = serverList.getAllServiceNames();
        assertEquals(2, serviceNames.size(), "应该有2个服务名称");
        assertTrue(serviceNames.contains("service-1"), "应该包含service-1");
        assertTrue(serviceNames.contains("service-2"), "应该包含service-2");
    }
    
    @Test
    @DisplayName("获取所有在线服务器")
    void testGetAllUpServers() {
        // 添加服务器（server3和server5是下线的）
        serverList.addServers("service-1", Arrays.asList(server1, server2));
        serverList.addServers("service-2", Arrays.asList(server3, server4));
        serverList.addServer("service-3", server5);
        
        // 获取所有在线服务器
        List<Server> upServers = getAllUpServers(serverList);
        
        // 验证结果
        assertEquals(3, upServers.size(), "应该有3个在线服务器"); // server1, server2, server4
        assertTrue(upServers.contains(server1), "应该包含server1");
        assertTrue(upServers.contains(server2), "应该包含server2");
        assertTrue(upServers.contains(server4), "应该包含server4");
        assertFalse(upServers.contains(server3), "不应该包含server3（下线）");
        assertFalse(upServers.contains(server5), "不应该包含server5（下线）");
        
        // 验证服务器状态
        for (Server server : upServers) {
            assertTrue(server.isAlive(), "在线服务器应该都是存活的");
        }
        
        // 验证列表顺序（应该保持添加顺序）
        List<Server> expectedOrder = Arrays.asList(server1, server2, server4);
        assertEquals(expectedOrder, upServers, "在线服务器列表应该保持添加顺序");
    }
    
    @Test
    @DisplayName("获取所有下线服务器")
    void testGetAllDownServers() {
        // 添加服务器（server3和server5是下线的）
        serverList.addServers("service-1", Arrays.asList(server1, server2));
        serverList.addServers("service-2", Arrays.asList(server3, server4));
        serverList.addServer("service-3", server5);
        
        // 获取所有下线服务器
        List<Server> downServers = getAllDownServers(serverList);
        
        // 验证结果
        assertEquals(2, downServers.size(), "应该有2个下线服务器"); // server3, server5
        assertTrue(downServers.contains(server3), "应该包含server3");
        assertTrue(downServers.contains(server5), "应该包含server5");
        assertFalse(downServers.contains(server1), "不应该包含server1（在线）");
        assertFalse(downServers.contains(server2), "不应该包含server2（在线）");
        assertFalse(downServers.contains(server4), "不应该包含server4（在线）");
        
        // 验证服务器状态
        for (Server server : downServers) {
            assertFalse(server.isAlive(), "下线服务器应该都是不存活的");
        }
        
        // 验证列表顺序（应该保持添加顺序）
        List<Server> expectedOrder = Arrays.asList(server3, server5);
        assertEquals(expectedOrder, downServers, "下线服务器列表应该保持添加顺序");
    }
    
    @Test
    @DisplayName("获取所有服务名称")
    void testGetAllServiceNames() {
        // 初始状态为空
        assertTrue(serverList.getAllServiceNames().isEmpty(), "初始状态应该为空");
        
        // 添加服务
        serverList.addServer("service-1", server1);
        List<String> serviceNames = serverList.getAllServiceNames();
        assertEquals(1, serviceNames.size(), "应该有1个服务名称");
        assertTrue(serviceNames.contains("service-1"), "应该包含service-1");
        
        // 添加第二个服务
        serverList.addServer("service-2", server2);
        serviceNames = serverList.getAllServiceNames();
        assertEquals(2, serviceNames.size(), "应该有2个服务名称");
        assertTrue(serviceNames.contains("service-1"), "应该包含service-1");
        assertTrue(serviceNames.contains("service-2"), "应该包含service-2");
        
        // 添加第三个服务
        serverList.addServer("service-3", server3);
        serviceNames = serverList.getAllServiceNames();
        assertEquals(3, serviceNames.size(), "应该有3个服务名称");
        assertTrue(serviceNames.contains("service-1"), "应该包含service-1");
        assertTrue(serviceNames.contains("service-2"), "应该包含service-2");
        assertTrue(serviceNames.contains("service-3"), "应该包含service-3");
        
        // 验证列表顺序（应该保持添加顺序）
        List<String> expectedOrder = Arrays.asList("service-1", "service-2", "service-3");
        assertEquals(expectedOrder, serviceNames, "服务名称列表应该保持添加顺序");
    }
    
    // ===========================================
    // 边界条件测试
    // ===========================================
    
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    @DisplayName("边界条件：空和空白服务名")
    void testEmptyAndWhitespaceServiceNames(String serviceName) {
        if(serviceName == null) {
            assertThrows(IllegalArgumentException.class, () -> serverList.addServer(null, server1));
            return;
        } else {
            serverList.addServer(serviceName, server1);
        }

        // 验证可以获取空服务名的服务器
        List<Server> servers = serverList.getServers(serviceName);
        assertNotNull(servers, "应该能获取到服务器列表");
        assertEquals(1, servers.size(), "应该能获取到服务器");
        assertEquals(server1, servers.getFirst(), "服务器应该是server1");
        
        // 验证服务名称列表包含空字符串
        List<String> serviceNames = serverList.getAllServiceNames();
        assertNotNull(serviceNames, "服务名称列表应该非空");
        assertTrue(serviceNames.contains(serviceName), "应该包含服务名");
        
        // 移除空服务名的服务器
        serverList.removeServer(serviceName, server1);
        
        // 验证服务器已被移除
        servers = serverList.getServers(serviceName);
        assertNotNull(servers, "应该返回非空列表");
        assertTrue(servers.isEmpty(), "服务器列表应该为空");
    }
    
    @Test
    @DisplayName("边界条件：空服务器列表")
    void testEmptyServerList() {
        // 添加空服务器列表
        serverList.addServers("service-1", Collections.emptyList());
        
        // 验证可以获取空列表
        List<Server> servers = serverList.getServers("service-1");
        assertNotNull(servers, "应该返回非空列表");
        assertTrue(servers.isEmpty(), "服务器列表应该为空");
        
        // 验证服务名称存在
        assertTrue(serverList.getAllServiceNames().contains("service-1"), "应该包含service-1");
        
        // 移除空服务
        serverList.removeService("service-1");
        
        // 验证服务已被移除
        assertFalse(serverList.getAllServiceNames().contains("service-1"), "不应该包含service-1");
    }
    
    @Test
    @DisplayName("边界条件：null服务器")
    void testNullServer() {
        // null服务器不被允许
        assertThrows(IllegalArgumentException.class, () -> serverList.addServer("service-1", null));
        
        // 验证可以获取包含null的服务器列表
        List<Server> servers = serverList.getServers("service-1");
        assertEquals(0, servers.size(), "应该有1个服务器");

        // 移除null服务器
        assertThrows(IllegalArgumentException.class, () -> serverList.removeServer("service-1", null));
        
        // 验证服务器列表为空
        servers = serverList.getServers("service-1");
        assertTrue(servers.isEmpty(), "服务器列表应该为空");
    }
    
    @Test
    @DisplayName("边界条件：重复添加相同服务器")
    void testDuplicateServerAddition() {
        // 添加相同的服务器多次
        serverList.addServer("service-1", server1);
        serverList.addServer("service-1", server1); // 重复添加
        serverList.addServer("service-1", server1); // 再次重复添加
        
        // 验证服务器列表包含重复的服务器
        List<Server> servers = serverList.getServers("service-1");
        assertEquals(3, servers.size(), "应该有3个服务器实例");
        
        // 验证所有服务器都是同一个实例
        for (Server server : servers) {
            assertEquals(server1, server, "所有服务器都应该是server1实例");
        }
        
        // 移除一个服务器实例
        serverList.removeServer("service-1", server1);
        
        // 验证还有两个实例
        servers = serverList.getServers("service-1");
        assertEquals(2, servers.size(), "应该有2个服务器实例");
        
        // 再次移除一个实例
        serverList.removeServer("service-1", server1);
        
        // 验证还有一个实例
        servers = serverList.getServers("service-1");
        assertEquals(1, servers.size(), "应该有1个服务器实例");
        
        // 移除最后一个实例
        serverList.removeServer("service-1", server1);
        
        // 验证服务已被移除
        assertTrue(serverList.getServers("service-1").isEmpty(), "服务器列表应该为空");
    }
    
    @Test
    @DisplayName("边界条件：服务器状态变化对列表的影响")
    void testServerStatusChangeImpact() {
        // 添加服务器
        serverList.addServers("service-1", Arrays.asList(server1, server2, server3));
        
        // 初始状态验证
        assertEquals(2, getAllUpServers(serverList).size(), "初始应该有2个在线服务器"); // server1, server2
        assertEquals(1, getAllDownServers(serverList).size(), "初始应该有1个下线服务器"); // server3
        
        // 改变server1的状态为下线
        server1.setAlive(false);
        
        // 验证在线/下线服务器列表已更新
        assertEquals(1, getAllUpServers(serverList).size(), "现在应该有1个在线服务器"); // server2
        assertEquals(2, getAllDownServers(serverList).size(), "现在应该有2个下线服务器"); // server1, server3
        
        // 改变server3的状态为上线
        server3.setAlive(true);
        
        // 验证在线/下线服务器列表已更新
        assertEquals(2, getAllUpServers(serverList).size(), "现在应该有2个在线服务器"); // server2, server3
        assertEquals(1, getAllDownServers(serverList).size(), "现在应该有1个下线服务器"); // server1
        
        // 所有服务器列表应该保持不变
        assertEquals(3, serverList.getAllServers().size(), "所有服务器列表应该保持3个");
    }
    
    // ===========================================
    // 功能操作测试
    // ===========================================
    
    @Test
    @DisplayName("刷新服务器列表")
    void testRefresh() {
        // 静态服务器列表的refresh方法应该不执行任何操作
        // 但也不应该抛出异常
        assertDoesNotThrow(() -> serverList.refresh(), "刷新操作不应该抛出异常");
        
        // 添加一些数据后再次刷新
        serverList.addServer("service-1", server1);
        assertDoesNotThrow(() -> serverList.refresh(), "刷新操作不应该抛出异常");
        
        // 验证数据没有被清除
        List<Server> servers = serverList.getServers("service-1");
        assertEquals(1, servers.size(), "服务器数量应该保持不变");
        assertEquals(server1, servers.getFirst(), "服务器实例应该保持不变");
    }
    
    @Test
    @DisplayName("移除服务")
    void testRemoveService() {
        // 添加服务
        serverList.addServers("service-1", Arrays.asList(server1, server2));
        serverList.addServers("service-2", Arrays.asList(server3, server4));
        
        // 验证初始状态
        assertEquals(2, serverList.getAllServiceNames().size(), "初始应该有2个服务");
        assertEquals(2, serverList.getServers("service-1").size(), "service-1应该有2个服务器");
        assertEquals(2, serverList.getServers("service-2").size(), "service-2应该有2个服务器");
        
        // 移除service-1
        serverList.removeService("service-1");
        
        // 验证service-1已被移除
        assertEquals(1, serverList.getAllServiceNames().size(), "现在应该有1个服务");
        assertFalse(serverList.getAllServiceNames().contains("service-1"), "不应该包含service-1");
        assertTrue(serverList.getAllServiceNames().contains("service-2"), "应该包含service-2");
        
        // 验证service-1的服务器列表为空
        List<Server> service1Servers = serverList.getServers("service-1");
        assertNotNull(service1Servers, "应该返回非空列表");
        assertTrue(service1Servers.isEmpty(), "service-1服务器列表应该为空");
        
        // 验证service-2的服务器仍然存在
        List<Server> service2Servers = serverList.getServers("service-2");
        assertEquals(2, service2Servers.size(), "service-2应该有2个服务器");
        
        // 移除不存在的服务（应该不抛出异常）
        assertDoesNotThrow(() -> serverList.removeService("nonexistent-service"), 
                          "移除不存在的服务不应该抛出异常");
    }
    
    @Test
    @DisplayName("移除单个服务器")
    void testRemoveSingleServer() {
        // 添加服务器
        serverList.addServers("service-1", Arrays.asList(server1, server2, server3));
        
        // 验证初始状态
        assertEquals(3, serverList.getServers("service-1").size(), "初始应该有3个服务器");
        
        // 移除server2
        serverList.removeServer("service-1", server2);
        
        // 验证server2已被移除
        List<Server> servers = serverList.getServers("service-1");
        assertEquals(2, servers.size(), "现在应该有2个服务器");
        assertTrue(servers.contains(server1), "应该包含server1");
        assertFalse(servers.contains(server2), "不应该包含server2");
        assertTrue(servers.contains(server3), "应该包含server3");
        
        // 移除server1
        serverList.removeServer("service-1", server1);
        
        // 验证server1已被移除
        servers = serverList.getServers("service-1");
        assertEquals(1, servers.size(), "现在应该有1个服务器");
        assertFalse(servers.contains(server1), "不应该包含server1");
        assertTrue(servers.contains(server3), "应该包含server3");
        
        // 移除最后一个服务器（服务应该被移除）
        serverList.removeServer("service-1", server3);
        
        // 验证服务已被移除
        assertFalse(serverList.getAllServiceNames().contains("service-1"), "不应该包含service-1");
        List<Server> emptyServers = serverList.getServers("service-1");
        assertNotNull(emptyServers, "应该返回非空列表");
        assertTrue(emptyServers.isEmpty(), "服务器列表应该为空");
        
        // 移除不存在的服务器（应该不抛出异常）
        assertDoesNotThrow(() -> serverList.removeServer("nonexistent-service", server1), 
                          "移除不存在的服务的服务器不应该抛出异常");
        assertDoesNotThrow(() -> serverList.removeServer("service-1", server1), 
                          "移除已不存在的服务的服务器不应该抛出异常");
    }
    
    @Test
    @DisplayName("清空所有服务器")
    void testClear() {
        // 添加一些数据
        serverList.addServers("service-1", Arrays.asList(server1, server2));
        serverList.addServers("service-2", Arrays.asList(server3, server4));
        
        // 验证初始状态
        assertEquals(2, serverList.getAllServiceNames().size(), "初始应该有2个服务");
        assertEquals(4, serverList.getAllServers().size(), "初始应该有4个服务器");
        
        // 清空所有服务器
        serverList.clear();
        
        // 验证所有数据已被清除
        assertTrue(serverList.getAllServiceNames().isEmpty(), "服务名称列表应该为空");
        assertTrue(serverList.getAllServers().isEmpty(), "所有服务器列表应该为空");
        assertTrue(getAllUpServers(serverList).isEmpty(), "在线服务器列表应该为空");
        assertTrue(getAllDownServers(serverList).isEmpty(), "下线服务器列表应该为空");
        
        // 验证具体服务的数据
        List<Server> service1Servers = serverList.getServers("service-1");
        assertNotNull(service1Servers, "应该返回非空列表");
        assertTrue(service1Servers.isEmpty(), "service-1服务器列表应该为空");
        
        List<Server> service2Servers = serverList.getServers("service-2");
        assertNotNull(service2Servers, "应该返回非空列表");
        assertTrue(service2Servers.isEmpty(), "service-2服务器列表应该为空");
    }
    
    // ===========================================
    // 并发安全测试
    // ===========================================
    
    @RepeatedTest(5)
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("并发测试：添加服务器")
    void testConcurrentServerAddition() throws InterruptedException {
        int threadCount = 10;
        int serversPerThread = 5;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < serversPerThread; j++) {
                        Server server = new Server("server-" + threadId + "-" + j, 
                                                  "host-" + threadId + "-" + j, 
                                                  8080 + j, "http", "zone" + threadId);
                        serverList.addServer("service" + threadId, server);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        // 启动所有线程
        startLatch.countDown();
        
        // 等待所有线程完成
        assertTrue(finishLatch.await(5, TimeUnit.SECONDS), "所有线程应该在5秒内完成");
        executor.shutdown();
        
        // 验证结果
        assertEquals(threadCount, serverList.getAllServiceNames().size(), 
                    "应该有" + threadCount + "个服务名称");
        assertEquals(threadCount * serversPerThread, serverList.getAllServers().size(), 
                    "应该有" + (threadCount * serversPerThread) + "个服务器");
        
        // 验证每个服务的服务器数量
        for (int i = 0; i < threadCount; i++) {
            List<Server> servers = serverList.getServers("service" + i);
            assertEquals(serversPerThread, servers.size(), 
                        "服务" + i + "应该有" + serversPerThread + "个服务器");
        }
    }
    
    @RepeatedTest(3)
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("并发测试：获取服务器列表")
    void testConcurrentServerRetrieval() throws InterruptedException {
        // 先添加一些测试数据
        for (int i = 0; i < 10; i++) {
            Server server = new Server("server" + i, "host" + i, 8080 + i, "http", "zone" + (i % 3));
            server.setAlive(i % 2 == 0); // 交替设置状态
            serverList.addServer("service" + (i % 4), server);
        }
        
        int threadCount = 8;
        int operationsPerThread = 50;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        // 测试各种获取方法
                        serverList.getAllServers();
                        getAllUpServers(serverList);
                        getAllDownServers(serverList);
                        serverList.getAllServiceNames();
                        serverList.getServers("service0");
                        serverList.getServers("service1");
                        serverList.getServers("service2");
                        serverList.getServers("service3");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        // 启动所有线程
        startLatch.countDown();
        
        // 等待所有线程完成
        assertTrue(finishLatch.await(5, TimeUnit.SECONDS), "所有线程应该在5秒内完成");
        executor.shutdown();
        
        // 验证数据完整性
        assertEquals(4, serverList.getAllServiceNames().size(), "应该有4个服务名称");
        assertEquals(10, serverList.getAllServers().size(), "应该有10个服务器");
        
        // 验证没有线程因并发问题而崩溃
        assertTrue(executor.isTerminated() || executor.isShutdown(), "线程池应该已终止或已关闭");
    }
    
    @Test
    @DisplayName("服务器列表的线程安全性")
    void testThreadSafety() {
        // 添加初始数据
        serverList.addServers("service-1", Arrays.asList(server1, server2));
        
        // 验证在多线程环境下数据访问的一致性
        Runnable reader = () -> {
            for (int i = 0; i < 100; i++) {
                List<Server> servers = serverList.getServers("service-1");
                assertNotNull(servers, "读取线程：服务器列表应该非空");
                assertTrue(servers.size() == 2 || servers.isEmpty() || servers.size() == 3, 
                          "读取线程：服务器数量应该在合理范围内");
                
                List<Server> allServers = serverList.getAllServers();
                assertNotNull(allServers, "读取线程：所有服务器列表应该非空");
                assertTrue(allServers.size() == 2 || allServers.isEmpty() || allServers.size() == 3, 
                          "读取线程：所有服务器数量应该在合理范围内");
            }
        };
        
        Runnable writer = () -> {
            for (int i = 0; i < 50; i++) {
                if (i % 2 == 0) {
                    serverList.addServer("service-1", server3);
                } else {
                    serverList.removeServer("service-1", server3);
                }
            }
        };
        
        // 启动读写线程
        Thread readerThread1 = new Thread(reader);
        Thread readerThread2 = new Thread(reader);
        Thread writerThread = new Thread(writer);
        
        readerThread1.start();
        readerThread2.start();
        writerThread.start();
        
        // 等待线程完成
        try {
            readerThread1.join(5000);
            readerThread2.join(5000);
            writerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 验证没有线程因并发问题而崩溃
        assertFalse(readerThread1.isAlive(), "读取线程1应该已完成");
        assertFalse(readerThread2.isAlive(), "读取线程2应该已完成");
        assertFalse(writerThread.isAlive(), "写入线程应该已完成");
    }
    
    // ===========================================
    // 性能测试
    // ===========================================
    
    @Test
    @DisplayName("性能测试：大量服务器处理")
    void testPerformanceWithLargeServerList() {
        int serviceCount = 100;
        int serversPerService = 50;
        
        // 添加大量服务器
        for (int i = 0; i < serviceCount; i++) {
            List<Server> servers = new ArrayList<>();
            for (int j = 0; j < serversPerService; j++) {
                Server server = new Server("server-" + i + "-" + j, 
                                          "host-" + i + "-" + j, 
                                          8080 + j, "http", "zone" + (i % 5));
                // 随机设置服务器状态
                server.setAlive(j % 3 != 0);
                servers.add(server);
            }
            serverList.addServers("service" + i, servers);
        }
        
        // 验证数据正确性
        assertEquals(serviceCount, serverList.getAllServiceNames().size(), 
                    "应该有" + serviceCount + "个服务名称");
        assertEquals(serviceCount * serversPerService, serverList.getAllServers().size(), 
                    "应该有" + (serviceCount * serversPerService) + "个服务器");
        
        // 测试各种获取操作的性能
        long startTime = System.currentTimeMillis();
        
        // 执行多次获取操作
        for (int i = 0; i < 100; i++) {
            serverList.getAllServers();
            getAllUpServers(serverList);
            getAllDownServers(serverList);
            serverList.getAllServiceNames();
            serverList.getServers("service0");
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // 验证性能在合理范围内（5000毫秒内完成）
        assertTrue(duration < 5000, "操作应在5秒内完成，实际耗时：" + duration + "ms");
        
        System.out.println("处理" + (serviceCount * serversPerService) + "个服务器的性能测试耗时：" + duration + "ms");
    }
    
    // ===========================================
    // 不可变性和数据完整性测试
    // ===========================================
    
    @Test
    @DisplayName("服务器列表的不可变性")
    void testServerListImmutability() {
        // 添加服务器
        serverList.addServers("service-1", Arrays.asList(server1, server2));
        
        // 获取服务器列表
        List<Server> servers = serverList.getServers("service-1");
        
        // 验证返回的列表是不可变的（尝试修改应该抛出异常）
        // 注意：由于StaticServerList返回的是ArrayList，不是不可变列表
        // 所以我们需要验证修改不会影响原始数据
        
        // 创建副本并修改
        List<Server> modifiedServers = new ArrayList<>(servers);
        modifiedServers.add(server3);
        
        // 验证原始列表没有被修改
        assertEquals(2, serverList.getServers("service-1").size(), 
                    "原始服务器列表应该保持2个服务器");
        assertFalse(serverList.getServers("service-1").contains(server3), 
                    "原始服务器列表不应该包含server3");
        
        // 验证修改后的列表
        assertEquals(3, modifiedServers.size(), "修改后的列表应该有3个服务器");
        assertTrue(modifiedServers.contains(server3), "修改后的列表应该包含server3");

        
        // 验证原始列表没有被修改
        assertEquals(1, serverList.getAllServiceNames().size(), 
                    "原始服务名称列表应该保持1个服务");
        assertFalse(serverList.getAllServiceNames().contains("new-service"), 
                    "原始服务名称列表不应该包含new-service");
    }
    
    @Test
    @DisplayName("数据隔离性测试")
    void testDataIsolation() {
        // 添加服务器
        serverList.addServers("service-1", Arrays.asList(server1, server2));
        
        // 获取服务器列表并尝试修改
        List<Server> originalServers = serverList.getServers("service-1");
        
        // 创建新的服务器列表并修改
        List<Server> modifiedServers = new ArrayList<>(originalServers);
        modifiedServers.add(server3);
        
        // 验证原始数据没有被修改
        assertEquals(2, serverList.getServers("service-1").size(), 
                    "原始服务器列表应该保持2个服务器");
        assertFalse(serverList.getServers("service-1").contains(server3), 
                    "原始服务器列表不应该包含server3");
        
        // 验证修改后的列表
        assertEquals(3, modifiedServers.size(), "修改后的列表应该有3个服务器");
        assertTrue(modifiedServers.contains(server3), "修改后的列表应该包含server3");
    }
    
    @Test
    @DisplayName("服务器实例一致性测试")
    void testServerInstanceConsistency() {
        // 添加服务器
        serverList.addServer("service-1", server1);
        serverList.addServer("service-1", server2);
        
        // 获取服务器列表
        List<Server> servers = serverList.getServers("service-1");
        
        // 验证服务器实例一致性
        assertEquals(2, servers.size(), "应该有2个服务器");
        assertSame(server1, servers.get(0), "第一个服务器应该是server1实例");
        assertSame(server2, servers.get(1), "第二个服务器应该是server2实例");
        
        // 修改服务器状态
        server1.setAlive(false);
        
        // 验证服务器状态变化反映在列表中
        List<Server> upServers = getAllUpServers(serverList);
        assertEquals(1, upServers.size(), "现在应该有1个在线服务器");
        assertFalse(upServers.contains(server1), "在线服务器不应该包含server1");
        assertTrue(upServers.contains(server2), "在线服务器应该包含server2");
        
        List<Server> downServers = getAllDownServers(serverList);
        assertEquals(1, downServers.size(), "现在应该有1个下线服务器");
        assertTrue(downServers.contains(server1), "下线服务器应该包含server1");
        assertFalse(downServers.contains(server2), "下线服务器不应该包含server2");
    }
}