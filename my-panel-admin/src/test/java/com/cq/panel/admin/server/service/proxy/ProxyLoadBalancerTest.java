package com.cq.panel.admin.server.service.proxy;

import com.cq.panel.common.loadbalancer.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProxyLoadBalancerTest {

    @Mock
    private ProxyHealthChecker healthChecker;

    @Mock
    private ProxyLoadBalancerConfig config;

    private ProxyServerList serverList;
    private ProxyLoadBalancer loadBalancer;

    @BeforeEach
    void setUp() {
        serverList = ProxyServerList.fromUrls("http://10.0.0.1:9876,http://10.0.0.2:9876,http://10.0.0.3:9876");
        lenient().when(config.getRetryCount()).thenReturn(2);
        lenient().when(config.isHealthCheckEnabled()).thenReturn(false);
        lenient().when(config.getHealthCheckInterval()).thenReturn(30000L);
        lenient().when(config.getHealthCheckFailureThreshold()).thenReturn(3);
        lenient().when(config.getHealthCheckSuccessThreshold()).thenReturn(2);
        loadBalancer = new ProxyLoadBalancer(serverList, healthChecker, config);
    }

    // ==================== 1. chooseServer测试 ====================

    @Nested
    @DisplayName("1. chooseServer")
    class ChooseServer {

        @Test
        @DisplayName("1.1 轮询选择Server")
        void testRoundRobin() {
            Server s1 = loadBalancer.chooseServer();
            Server s2 = loadBalancer.chooseServer();
            Server s3 = loadBalancer.chooseServer();
            assertNotEquals(s1, s2);
            assertNotEquals(s2, s3);
        }

        @Test
        @DisplayName("1.2 空列表返回null")
        void testEmptyList() {
            ProxyServerList empty = ProxyServerList.fromUrls("");
            ProxyLoadBalancer lb = new ProxyLoadBalancer(empty, healthChecker, config);
            assertNull(lb.chooseServer());
        }
    }

    // ==================== 2. executeWithFailover测试 ====================

    @Nested
    @DisplayName("2. executeWithFailover")
    class ExecuteWithFailover {

        @Test
        @DisplayName("2.1 正常请求成功")
        void testSuccess() {
            String result = loadBalancer.executeWithFailover(server -> server.getUrl() + "/api/test");
            assertNotNull(result);
            assertTrue(result.startsWith("http://10.0.0."));
        }

        @Test
        @DisplayName("2.2 首次失败自动重试下一个Server")
        void testFailoverOnFailure() {
            // 记录调用顺序
            StringBuilder calls = new StringBuilder();
            String result = loadBalancer.executeWithFailover(server -> {
                String host = server.getHost();
                calls.append(host).append(",");
                if (host.equals("10.0.0.1")) {
                    throw new RuntimeException("Connection refused");
                }
                return "success from " + host;
            });
            assertTrue(result.startsWith("success from"));
            assertTrue(calls.toString().contains("10.0.0.1"));
        }

        @Test
        @DisplayName("2.3 所有Server失败抛出IllegalStateException")
        void testAllServersFail() {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    loadBalancer.executeWithFailover(server -> {
                        throw new RuntimeException("fail: " + server.getHost());
                    }));
            assertTrue(ex.getMessage().contains("所有Proxy Server均不可用"));
        }

        @Test
        @DisplayName("2.4 空Server列表抛出IllegalStateException")
        void testEmptyServerList() {
            ProxyServerList empty = ProxyServerList.fromUrls("");
            ProxyLoadBalancer lb = new ProxyLoadBalancer(empty, healthChecker, config);
            assertThrows(IllegalStateException.class,
                    () -> lb.executeWithFailover(server -> "ok"));
        }

        @Test
        @DisplayName("2.5 失败后标记Server宕机")
        void testMarkDownOnFailure() {
            assertEquals(3, serverList.getAliveServers().size());
            try {
                loadBalancer.executeWithFailover(server -> {
                    throw new RuntimeException("fail");
                });
            } catch (IllegalStateException ignored) {
            }
            // 重试后应该有Server被标记宕机
            assertTrue(serverList.getAliveServers().size() < 3);
        }
    }

    // ==================== 3. 健康检查测试 ====================

    @Nested
    @DisplayName("3. 健康检查")
    class HealthCheck {

        @Test
        @DisplayName("3.1 performHealthCheck调用healthChecker.checkAll")
        void testPerformHealthCheck() {
            loadBalancer.performHealthCheck();
            verify(healthChecker).checkAll(serverList, 3, 2);
        }

        @Test
        @DisplayName("3.2 健康检查异常不影响后续运行")
        void testHealthCheckException() {
            doThrow(new RuntimeException("check error")).when(healthChecker)
                    .checkAll(any(), anyInt(), anyInt());
            // 不应抛异常
            assertDoesNotThrow(() -> loadBalancer.performHealthCheck());
        }
    }
}
