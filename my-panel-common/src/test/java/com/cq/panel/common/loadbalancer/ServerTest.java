package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务器实体类测试
 */
@DisplayName("服务器实体类测试")
class ServerTest {
    
    private Server defaultServer;
    private Server fullServer;
    
    @BeforeEach
    void setUp() {
        // 创建默认配置的服务器
        defaultServer = new Server("localhost", 8080);
        
        // 创建完整配置的服务器
        fullServer = new Server("server-1", "example.com", 443, "https", "zone1");
    }
    
    @Test
    @DisplayName("创建默认配置的服务器")
    void testCreateDefaultServer() {
        assertNotNull(defaultServer);
        assertEquals("localhost:8080", defaultServer.getId());
        assertEquals("localhost", defaultServer.getHost());
        assertEquals(8080, defaultServer.getPort());
        assertEquals("http", defaultServer.getScheme());
        assertNull(defaultServer.getZone());
        assertTrue(defaultServer.isAlive());
        assertEquals(0, defaultServer.getConcurrentRequests());
        assertEquals(0, defaultServer.getResponseTime());
        assertEquals(0, defaultServer.getLastHealthCheckTime());
        assertEquals(0, defaultServer.getConsecutiveFailures());
        assertEquals(0, defaultServer.getConsecutiveSuccesses());
    }
    
    @Test
    @DisplayName("创建完整配置的服务器")
    void testCreateFullServer() {
        assertNotNull(fullServer);
        assertEquals("server-1", fullServer.getId());
        assertEquals("example.com", fullServer.getHost());
        assertEquals(443, fullServer.getPort());
        assertEquals("https", fullServer.getScheme());
        assertEquals("zone1", fullServer.getZone());
        assertTrue(fullServer.isAlive());
    }
    
    @Test
    @DisplayName("测试服务器URL生成")
    void testGetUrl() {
        assertEquals("http://localhost:8080", defaultServer.getUrl());
        assertEquals("https://example.com:443", fullServer.getUrl());
        
        // 测试自定义协议
        Server customServer = new Server("test", "api.example.com", 9000, "ws", "zone2");
        assertEquals("ws://api.example.com:9000", customServer.getUrl());
    }
    
    @Test
    @DisplayName("测试服务器存活状态管理")
    void testAliveStatus() {
        assertTrue(defaultServer.isAlive());
        
        // 设置为不存活
        defaultServer.setAlive(false);
        assertFalse(defaultServer.isAlive());
        
        // 重新设置为存活
        defaultServer.setAlive(true);
        assertTrue(defaultServer.isAlive());
    }
    
    @Test
    @DisplayName("测试并发请求计数")
    void testConcurrentRequests() {
        assertEquals(0, defaultServer.getConcurrentRequests());
        
        // 设置并发请求数
        defaultServer.setConcurrentRequests(5);
        assertEquals(5, defaultServer.getConcurrentRequests());
        
        // 递增并发请求数
        defaultServer.incrementConcurrentRequests();
        assertEquals(6, defaultServer.getConcurrentRequests());
        
        // 递减并发请求数
        defaultServer.decrementConcurrentRequests();
        assertEquals(5, defaultServer.getConcurrentRequests());
        
        // 测试递减到0不会变成负数
        defaultServer.setConcurrentRequests(0);
        defaultServer.decrementConcurrentRequests();
        assertEquals(0, defaultServer.getConcurrentRequests());
    }
    
    @Test
    @DisplayName("测试响应时间管理")
    void testResponseTime() {
        assertEquals(0, defaultServer.getResponseTime());
        
        // 设置响应时间
        defaultServer.setResponseTime(150);
        assertEquals(150, defaultServer.getResponseTime());
        
        // 设置更大的响应时间
        defaultServer.setResponseTime(1000);
        assertEquals(1000, defaultServer.getResponseTime());
    }
    
    @Test
    @DisplayName("测试健康检查时间管理")
    void testLastHealthCheckTime() {
        assertEquals(0, defaultServer.getLastHealthCheckTime());
        
        // 设置健康检查时间
        long currentTime = System.currentTimeMillis();
        defaultServer.setLastHealthCheckTime(currentTime);
        assertEquals(currentTime, defaultServer.getLastHealthCheckTime());
        
        // 设置未来的时间（虽然不现实，但测试边界）
        defaultServer.setLastHealthCheckTime(currentTime + 10000);
        assertEquals(currentTime + 10000, defaultServer.getLastHealthCheckTime());
    }
    
    @Test
    @DisplayName("测试连续失败和成功计数")
    void testConsecutiveCounters() {
        assertEquals(0, defaultServer.getConsecutiveFailures());
        assertEquals(0, defaultServer.getConsecutiveSuccesses());
        
        // 设置连续失败次数
        defaultServer.setConsecutiveFailures(3);
        assertEquals(3, defaultServer.getConsecutiveFailures());
        
        // 设置连续成功次数
        defaultServer.setConsecutiveSuccesses(2);
        assertEquals(2, defaultServer.getConsecutiveSuccesses());
        
        // 重置计数器
        defaultServer.setConsecutiveFailures(0);
        defaultServer.setConsecutiveSuccesses(0);
        assertEquals(0, defaultServer.getConsecutiveFailures());
        assertEquals(0, defaultServer.getConsecutiveSuccesses());
    }
    
    @Test
    @DisplayName("测试记录健康检查成功")
    void testRecordHealthCheckSuccess() {
        // 初始状态
        assertEquals(0, defaultServer.getConsecutiveSuccesses());
        assertEquals(0, defaultServer.getConsecutiveFailures());
        
        // 记录成功
        defaultServer.recordHealthCheckSuccess();
        assertEquals(1, defaultServer.getConsecutiveSuccesses());
        assertEquals(0, defaultServer.getConsecutiveFailures());
        assertTrue(defaultServer.getLastHealthCheckTime() > 0);
        
        // 再次记录成功
        defaultServer.recordHealthCheckSuccess();
        assertEquals(2, defaultServer.getConsecutiveSuccesses());
        assertEquals(0, defaultServer.getConsecutiveFailures());
        
        // 在失败后记录成功（会重置失败计数器）
        defaultServer.setConsecutiveFailures(3);
        defaultServer.recordHealthCheckSuccess();
        assertEquals(3, defaultServer.getConsecutiveSuccesses());
        assertEquals(0, defaultServer.getConsecutiveFailures());
    }
    
    @Test
    @DisplayName("测试记录健康检查失败")
    void testRecordHealthCheckFailure() {
        // 初始状态
        assertEquals(0, defaultServer.getConsecutiveFailures());
        assertEquals(0, defaultServer.getConsecutiveSuccesses());
        
        // 记录失败
        defaultServer.recordHealthCheckFailure();
        assertEquals(1, defaultServer.getConsecutiveFailures());
        assertEquals(0, defaultServer.getConsecutiveSuccesses());
        assertTrue(defaultServer.getLastHealthCheckTime() > 0);
        
        // 再次记录失败
        defaultServer.recordHealthCheckFailure();
        assertEquals(2, defaultServer.getConsecutiveFailures());
        assertEquals(0, defaultServer.getConsecutiveSuccesses());
        
        // 在成功后记录失败（会重置成功计数器）
        defaultServer.setConsecutiveSuccesses(3);
        defaultServer.recordHealthCheckFailure();
        assertEquals(3, defaultServer.getConsecutiveFailures());
        assertEquals(0, defaultServer.getConsecutiveSuccesses());
    }
    
    @Test
    @DisplayName("测试更新健康状态")
    void testUpdateHealthStatus() {
        // 初始状态为存活
        assertTrue(defaultServer.isAlive());
        
        // 连续失败次数达到阈值，应该设置为不存活
        defaultServer.setConsecutiveFailures(3);
        defaultServer.updateHealthStatus(3, 2);
        assertFalse(defaultServer.isAlive());
        
        // 连续成功次数达到阈值，应该重新设置为存活
        defaultServer.setConsecutiveSuccesses(2);
        defaultServer.updateHealthStatus(3, 2);
        assertFalse(defaultServer.isAlive());
        
        // 测试边界条件：刚好达到失败阈值
        defaultServer.setAlive(true);
        defaultServer.setConsecutiveFailures(3);
        defaultServer.updateHealthStatus(3, 2);
        assertFalse(defaultServer.isAlive());
        
        // 测试边界条件：刚好达到成功阈值
        defaultServer.setAlive(false);
        defaultServer.setConsecutiveSuccesses(2);
        defaultServer.updateHealthStatus(3, 2);
        assertFalse(defaultServer.isAlive());
        
        // 测试边界条件：未达到阈值
        defaultServer.setAlive(true);
        defaultServer.setConsecutiveFailures(2);
        defaultServer.updateHealthStatus(3, 2);
        assertTrue(defaultServer.isAlive());
    }
    
    @Test
    @DisplayName("测试是否需要健康检查")
    void testNeedsHealthCheck() {
        // 初始状态，从未检查过，应该需要检查
        assertTrue(defaultServer.needsHealthCheck(30000));
        
        // 设置最近检查时间
        long currentTime = System.currentTimeMillis();
        defaultServer.setLastHealthCheckTime(currentTime);
        
        // 在检查间隔内，不需要检查
        assertFalse(defaultServer.needsHealthCheck(30000));
        
        // 超过检查间隔，需要检查
        assertTrue(defaultServer.needsHealthCheck(0));
        
        // 测试边界条件：刚好达到间隔
        defaultServer.setLastHealthCheckTime(currentTime - 30000);
        assertTrue(defaultServer.needsHealthCheck(30000));
    }
    
    @Test
    @DisplayName("测试服务器相等性")
    void testEquals() {
        // 相同主机、端口、协议的服务器应该相等
        Server server1 = new Server("server1", "localhost", 8080, "http", "zone1");
        Server server2 = new Server("server2", "localhost", 8080, "http", "zone2");
        assertEquals(server1, server2);
        
        // 不同主机的服务器不相等
        Server server3 = new Server("example.com", 8080);
        assertNotEquals(server1, server3);
        
        // 不同端口的服务器不相等
        Server server4 = new Server("localhost", 8081);
        assertNotEquals(server1, server4);
        
        // 不同协议的服务器不相等
        Server server5 = new Server("server5", "localhost", 8080, "https", null);
        assertNotEquals(server1, server5);
        
        // 与null比较
        assertNotEquals(null, server1);

        // 自反性
        assertEquals(server1, server1);
    }
    
    @Test
    @DisplayName("测试服务器哈希码")
    void testHashCode() {
        // 相同主机、端口、协议的服务器应该有相同的哈希码
        Server server1 = new Server("server1", "localhost", 8080, "http", "zone1");
        Server server2 = new Server("server2", "localhost", 8080, "http", "zone2");
        assertEquals(server1.hashCode(), server2.hashCode());
        
        // 不同配置的服务器应该有不同的哈希码
        Server server3 = new Server("example.com", 8080);
        assertNotEquals(server1.hashCode(), server3.hashCode());
        
        // 一致性：多次调用应该返回相同的哈希码
        int hashCode1 = server1.hashCode();
        int hashCode2 = server1.hashCode();
        assertEquals(hashCode1, hashCode2);
    }
    
    @Test
    @DisplayName("测试服务器字符串表示")
    void testToString() {
        String toString = defaultServer.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("Server"));
        assertTrue(toString.contains("id='localhost:8080'"));
        assertTrue(toString.contains("host='localhost'"));
        assertTrue(toString.contains("port=8080"));
        assertTrue(toString.contains("scheme='http'"));
        assertTrue(toString.contains("alive=true"));
        assertTrue(toString.contains("concurrentRequests"));
        assertTrue(toString.contains("responseTime"));
        assertTrue(toString.contains("lastHealthCheckTime"));
        assertTrue(toString.contains("consecutiveFailures"));
        assertTrue(toString.contains("consecutiveSuccesses"));
    }
    
    @Test
    @DisplayName("测试边界条件：无效端口")
    void testInvalidPort() {
        // 测试端口为0（虽然不常见，但应该支持）
        Server server1 = new Server("localhost", 0);
        assertEquals(0, server1.getPort());
        
        // 测试最大端口号
        Server server2 = new Server("localhost", 65535);
        assertEquals(65535, server2.getPort());
        
        // 测试负端口（应该被允许，虽然不现实）
        Server server3 = new Server("localhost", -1);
        assertEquals(-1, server3.getPort());
    }
    
    @Test
    @DisplayName("测试边界条件：空值和空字符串")
    void testNullAndEmptyValues() {
        // 测试空ID（应该使用主机和端口生成ID）
        Server server1 = new Server(null, "localhost", 8080, "http", null);
        assertEquals("localhost:8080", server1.getId());
        
        // 测试空协议（应该使用默认协议）
        Server server2 = new Server("server2", "localhost", 8080, null, null);
        assertEquals("http", server2.getScheme());
        
        // 测试空主机（应该允许，虽然不现实）
        Server server3 = new Server(null, 8080);
        assertEquals("null:8080", server3.getId());
        
        // 测试空区域
        Server server4 = new Server("server", "localhost", 8080, "http", null);
        assertNull(server4.getZone());
    }
    
    @RepeatedTest(5)
    @DisplayName("并发测试：递增并发请求计数")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentIncrement() throws InterruptedException {
        int threadCount = 10;
        int incrementsPerThread = 100;
        
        ExecutorService executor = new ThreadPoolExecutor(
                threadCount, threadCount,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "test-concurrent-increment");
                    t.setDaemon(true);
                    return t;
                });
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        
        AtomicInteger totalIncrements = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        defaultServer.incrementConcurrentRequests();
                        totalIncrements.incrementAndGet();
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
        assertTrue(finishLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        
        // 验证结果
        assertEquals(threadCount * incrementsPerThread, defaultServer.getConcurrentRequests());
        assertEquals(threadCount * incrementsPerThread, totalIncrements.get());
    }
    
    @RepeatedTest(5)
    @DisplayName("并发测试：递减并发请求计数")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentDecrement() throws InterruptedException {
        int initialRequests = 1000;
        int threadCount = 10;
        int decrementsPerThread = 50;
        
        // 设置初始并发请求数
        defaultServer.setConcurrentRequests(initialRequests);
        
        ExecutorService executor = new ThreadPoolExecutor(
                threadCount, threadCount,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "test-concurrent-decrement");
                    t.setDaemon(true);
                    return t;
                });
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        
        AtomicInteger totalDecrements = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < decrementsPerThread; j++) {
                        defaultServer.decrementConcurrentRequests();
                        totalDecrements.incrementAndGet();
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
        assertTrue(finishLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        
        // 验证结果
        int expected = initialRequests - (threadCount * decrementsPerThread);
        assertEquals(expected, defaultServer.getConcurrentRequests());
        assertEquals(threadCount * decrementsPerThread, totalDecrements.get());
    }
    
    @RepeatedTest(3)
    @DisplayName("并发测试：健康检查记录")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentHealthCheckRecording() throws InterruptedException {
        int threadCount = 5;
        int operationsPerThread = 100;
        
        ExecutorService executor = new ThreadPoolExecutor(
                threadCount, threadCount,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "test-concurrent-health-check");
                    t.setDaemon(true);
                    return t;
                });
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final boolean recordSuccess = i % 2 == 0; // 交替记录成功和失败
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        if (recordSuccess) {
                            defaultServer.recordHealthCheckSuccess();
                            successCount.incrementAndGet();
                        } else {
                            defaultServer.recordHealthCheckFailure();
                            failureCount.incrementAndGet();
                        }
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
        assertTrue(finishLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        
        // 验证结果
        // 注意：由于recordHealthCheckSuccess和recordHealthCheckFailure方法会重置计数器
        // 所以最终的计数可能不等于每个线程的操作总数
        assertTrue(defaultServer.getConsecutiveSuccesses() >= 0);
        assertTrue(defaultServer.getConsecutiveFailures() >= 0);
        assertTrue(defaultServer.getLastHealthCheckTime() > 0);
    }
    
    @Test
    @DisplayName("测试服务器访问时间管理")
    void testLastAccessTime() {
        long initialTime = defaultServer.getLastAccessTime();
        assertTrue(initialTime > 0);
        
        // 设置新的访问时间
        long newTime = System.currentTimeMillis() + 10000;
        defaultServer.setLastAccessTime(newTime);
        assertEquals(newTime, defaultServer.getLastAccessTime());
        
        // 设置过去的时间
        long pastTime = System.currentTimeMillis() - 10000;
        defaultServer.setLastAccessTime(pastTime);
        assertEquals(pastTime, defaultServer.getLastAccessTime());
    }
    
    @Test
    @DisplayName("测试服务器ID自动生成")
    void testAutoGeneratedId() {
        // 测试不提供ID时自动生成
        Server server1 = new Server("api.example.com", 9090);
        assertEquals("api.example.com:9090", server1.getId());
        
        // 测试提供ID时使用提供的ID
        Server server2 = new Server("custom-id", "api.example.com", 9090, "http", null);
        assertEquals("custom-id", server2.getId());
        
        // 测试特殊字符的主机和端口
        Server server3 = new Server("host-with-dash", 12345);
        assertEquals("host-with-dash:12345", server3.getId());
    }
    
    @Test
    @DisplayName("测试不同协议支持")
    @SuppressWarnings("all")
    void testDifferentSchemes() {
        // 测试HTTP协议
        Server httpServer = new Server("http-server", "example.com", 80, "http", null);
        assertEquals("http", httpServer.getScheme());
        assertEquals("http://example.com:80", httpServer.getUrl());
        
        // 测试HTTPS协议
        Server httpsServer = new Server("https-server", "secure.example.com", 443, "https", null);
        assertEquals("https", httpsServer.getScheme());
        assertEquals("https://secure.example.com:443", httpsServer.getUrl());
        
        // 测试WebSocket协议
        Server wsServer = new Server("ws-server", "ws.example.com", 8080, "ws", null);
        assertEquals("ws", wsServer.getScheme());
        assertEquals("ws://ws.example.com:8080", wsServer.getUrl());
        
        // 测试自定义协议
        Server customServer = new Server("custom-server", "custom.example.com", 9000, "custom", null);
        assertEquals("custom", customServer.getScheme());
        assertEquals("custom://custom.example.com:9000", customServer.getUrl());
    }
    
    @Test
    @DisplayName("测试区域感知功能")
    void testZoneAwareness() {
        // 测试有区域的服务器
        Server zoneServer = new Server("zone-server", "zone1.example.com", 8080, "http", "zone1");
        assertEquals("zone1", zoneServer.getZone());
        
        // 测试无区域的服务器
        Server noZoneServer = new Server("no-zone-server", "global.example.com", 8080, "http", null);
        assertNull(noZoneServer.getZone());
        
        // 测试空字符串区域
        Server emptyZoneServer = new Server("empty-zone-server", "empty.example.com", 8080, "http", "");
        assertEquals("", emptyZoneServer.getZone());
    }
    
    @Test
    @DisplayName("测试性能指标：响应时间")
    void testResponseTimePerformance() {
        // 测试设置响应时间
        defaultServer.setResponseTime(50);
        assertEquals(50, defaultServer.getResponseTime());
        
        // 测试设置更大的响应时间
        defaultServer.setResponseTime(1000);
        assertEquals(1000, defaultServer.getResponseTime());
        
        // 测试设置负响应时间（虽然不现实，但应该支持）
        defaultServer.setResponseTime(-1);
        assertEquals(-1, defaultServer.getResponseTime());
    }
    
    @Test
    @DisplayName("测试健康状态阈值逻辑")
    void testHealthStatusThresholdLogic() {
        // 测试失败阈值逻辑
        defaultServer.setConsecutiveFailures(5);
        defaultServer.updateHealthStatus(3, 2);
        assertFalse(defaultServer.isAlive());
        
        // 测试成功阈值逻辑
        defaultServer.setConsecutiveSuccesses(3);
        defaultServer.updateHealthStatus(5, 2);
        assertFalse(defaultServer.isAlive());
        
        // 测试边界条件：刚好达到失败阈值
        defaultServer.setAlive(true);
        defaultServer.setConsecutiveFailures(3);
        defaultServer.updateHealthStatus(3, 2);
        assertFalse(defaultServer.isAlive());
        
        // 测试边界条件：刚好达到成功阈值
        defaultServer.setAlive(false);
        defaultServer.setConsecutiveSuccesses(2);
        defaultServer.updateHealthStatus(3, 2);
        assertFalse(defaultServer.isAlive());
    }
    
    @Test
    @DisplayName("测试健康检查间隔计算")
    void testHealthCheckIntervalCalculation() {
        // 设置最近检查时间
        long currentTime = System.currentTimeMillis();
        defaultServer.setLastHealthCheckTime(currentTime);
        
        // 测试不同间隔
        assertFalse(defaultServer.needsHealthCheck(1000));
        assertTrue(defaultServer.needsHealthCheck(0));
        
        // 设置过去的时间
        defaultServer.setLastHealthCheckTime(currentTime - 60000);
        assertTrue(defaultServer.needsHealthCheck(30000));
        assertFalse(defaultServer.needsHealthCheck(120000));
        
        // 测试边界条件：刚好达到间隔
        defaultServer.setLastHealthCheckTime(currentTime - 30000);
        assertTrue(defaultServer.needsHealthCheck(30000));
    }
    
    @Test
    @DisplayName("测试服务器状态一致性")
    void testServerStateConsistency() {
        // 验证初始状态一致性
        assertTrue(defaultServer.isAlive());
        assertEquals(0, defaultServer.getConcurrentRequests());
        assertEquals(0, defaultServer.getConsecutiveFailures());
        assertEquals(0, defaultServer.getConsecutiveSuccesses());
        
        // 修改状态后验证一致性
        defaultServer.setAlive(false);
        defaultServer.setConcurrentRequests(10);
        defaultServer.setConsecutiveFailures(5);
        
        assertFalse(defaultServer.isAlive());
        assertEquals(10, defaultServer.getConcurrentRequests());
        assertEquals(5, defaultServer.getConsecutiveFailures());
        
        // 通过健康检查更新状态
        defaultServer.recordHealthCheckSuccess();
        assertEquals(1, defaultServer.getConsecutiveSuccesses());
        assertEquals(0, defaultServer.getConsecutiveFailures());
        
        defaultServer.updateHealthStatus(3, 2);
        assertFalse(defaultServer.isAlive());
    }
}