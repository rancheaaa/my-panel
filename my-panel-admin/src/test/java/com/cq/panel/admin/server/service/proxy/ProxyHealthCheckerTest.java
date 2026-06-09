package com.cq.panel.admin.server.service.proxy;

import com.cq.panel.common.loadbalancer.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProxyHealthCheckerTest {

    @Mock
    private RestTemplate restTemplate;

    private ProxyHealthChecker healthChecker;

    @BeforeEach
    void setUp() {
        healthChecker = new ProxyHealthChecker(restTemplate);
    }

    @Nested
    @DisplayName("1. 单Server健康检查")
    class SingleCheck {

        @Test
        @DisplayName("1.1 返回200判定为健康")
        void testHealthy() {
            when(restTemplate.getForEntity(anyString(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            Server server = new Server("10.0.0.1", 9876);
            assertTrue(healthChecker.isHealthy(server));
            verify(restTemplate).getForEntity("http://10.0.0.1:9876/forward/ping", String.class);
        }

        @Test
        @DisplayName("1.2 返回500判定为不健康")
        void testUnhealthy5xx() {
            when(restTemplate.getForEntity(anyString(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("error", HttpStatus.INTERNAL_SERVER_ERROR));

            Server server = new Server("10.0.0.1", 9876);
            assertFalse(healthChecker.isHealthy(server));
        }

        @Test
        @DisplayName("1.3 连接异常判定为不健康")
        void testConnectionError() {
            when(restTemplate.getForEntity(anyString(), eq(String.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            Server server = new Server("10.0.0.1", 9876);
            assertFalse(healthChecker.isHealthy(server));
        }

        @Test
        @DisplayName("1.4 null参数返回false")
        void testNullServer() {
            assertFalse(healthChecker.isHealthy(null));
        }
    }

    @Nested
    @DisplayName("2. 批量健康检查")
    class BatchCheck {

        private ProxyServerList serverList;

        @BeforeEach
        void setUpList() {
            serverList = ProxyServerList.fromUrls("http://10.0.0.1:9876,http://10.0.0.2:9876");
        }

        @Test
        @DisplayName("2.1 所有健康时全部存活")
        void testAllHealthy() {
            when(restTemplate.getForEntity(anyString(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            healthChecker.checkAll(serverList, 3, 2);
            assertEquals(2, serverList.getAliveServers().size());
        }

        @Test
        @DisplayName("2.2 连续失败达到阈值标记宕机")
        void testFailureThreshold() {
            when(restTemplate.getForEntity(contains("10.0.0.1"), eq(String.class)))
                    .thenThrow(new RestClientException("Connection refused"));
            when(restTemplate.getForEntity(contains("10.0.0.2"), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            // 连续检查3次，达到failureThreshold=3
            for (int i = 0; i < 3; i++) {
                healthChecker.checkAll(serverList, 3, 2);
            }
            assertEquals(1, serverList.getAliveServers().size());
            assertEquals("10.0.0.2", serverList.getAliveServers().get(0).getHost());
        }

        @Test
        @DisplayName("2.3 宕机Server恢复后标记存活")
        void testRecovery() {
            // 先让10.0.0.1宕机
            Server s1 = serverList.getServers().get(0);
            serverList.markDown(s1);
            assertEquals(1, serverList.getAliveServers().size());

            // 模拟恢复
            when(restTemplate.getForEntity(anyString(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            // 连续成功达到successThreshold=2
            healthChecker.checkAll(serverList, 3, 2);
            healthChecker.checkAll(serverList, 3, 2);

            assertEquals(2, serverList.getAliveServers().size());
        }
    }
}
