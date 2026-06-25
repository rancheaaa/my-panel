package com.cq.panel.admin.server.service.proxy;

import com.cq.panel.common.loadbalancer.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProxyServerListTest {

    private ProxyServerList serverList;

    @BeforeEach
    void setUp() {
        serverList = ProxyServerList.fromUrls("http://10.0.0.1:9876,http://10.0.0.2:9876,http://10.0.0.3:9876");
    }

    // ==================== 1. URL解析测试 ====================

    @Nested
    @DisplayName("1. URL解析")
    class UrlParsing {

        @Test
        @DisplayName("1.1 逗号分隔的多URL正确解析")
        void testParseMultipleUrls() {
            assertEquals(3, serverList.getServers().size());
            assertEquals("http://10.0.0.1:9876", serverList.getServers().get(0).getUrl());
            assertEquals("http://10.0.0.2:9876", serverList.getServers().get(1).getUrl());
            assertEquals("http://10.0.0.3:9876", serverList.getServers().get(2).getUrl());
        }

        @Test
        @DisplayName("1.2 单个URL正确解析")
        void testParseSingleUrl() {
            ProxyServerList single = ProxyServerList.fromUrls("http://localhost:9876");
            assertEquals(1, single.getServers().size());
            assertEquals("http://localhost:9876", single.getServers().get(0).getUrl());
        }

        @Test
        @DisplayName("1.3 空格容错：逗号前后有空格")
        void testParseWithSpaces() {
            ProxyServerList list = ProxyServerList.fromUrls("http://10.0.0.1:9876 , http://10.0.0.2:9876 ");
            assertEquals(2, list.getServers().size());
            assertEquals("http://10.0.0.1:9876", list.getServers().get(0).getUrl());
        }

        @Test
        @DisplayName("1.4 空字符串返回空列表")
        void testParseEmpty() {
            ProxyServerList list = ProxyServerList.fromUrls("");
            assertTrue(list.getServers().isEmpty());
        }

        @Test
        @DisplayName("1.5 null返回空列表")
        void testParseNull() {
            ProxyServerList list = ProxyServerList.fromUrls(null);
            assertTrue(list.getServers().isEmpty());
        }

        @Test
        @DisplayName("1.6 仅空白字符返回空列表")
        void testParseBlank() {
            ProxyServerList list = ProxyServerList.fromUrls("   ");
            assertTrue(list.getServers().isEmpty());
        }

        @Test
        @DisplayName("1.7 重复URL去重")
        void testParseDuplicateUrls() {
            ProxyServerList list = ProxyServerList.fromUrls("http://10.0.0.1:9876,http://10.0.0.1:9876");
            assertEquals(1, list.getServers().size());
        }
    }

    // ==================== 2. 轮询选择测试 ====================

    @Nested
    @DisplayName("2. 轮询选择")
    class RoundRobin {

        @Test
        @DisplayName("2.1 轮询顺序正确")
        void testRoundRobinOrder() {
            Server s1 = serverList.next();
            Server s2 = serverList.next();
            Server s3 = serverList.next();
            Server s4 = serverList.next(); // 回到第一个

            assertEquals("10.0.0.1", s1.getHost());
            assertEquals("10.0.0.2", s2.getHost());
            assertEquals("10.0.0.3", s3.getHost());
            assertEquals("10.0.0.1", s4.getHost());
        }

        @Test
        @DisplayName("2.2 单Server时始终返回同一个")
        void testSingleServer() {
            ProxyServerList single = ProxyServerList.fromUrls("http://localhost:9876");
            Server s1 = single.next();
            Server s2 = single.next();
            assertEquals(s1, s2);
        }

        @Test
        @DisplayName("2.3 空列表时next返回null")
        void testEmptyListNext() {
            ProxyServerList empty = ProxyServerList.fromUrls("");
            assertNull(empty.next());
        }
    }

    // ==================== 3. 存活状态管理测试 ====================

    @Nested
    @DisplayName("3. 存活状态管理")
    class AliveStatus {

        @Test
        @DisplayName("3.1 初始状态所有Server存活")
        void testInitialAlive() {
            List<Server> alive = serverList.getAliveServers();
            assertEquals(3, alive.size());
        }

        @Test
        @DisplayName("3.2 标记宕机后从存活列表移除")
        void testMarkDown() {
            Server s1 = serverList.getServers().get(0);
            serverList.markDown(s1);
            List<Server> alive = serverList.getAliveServers();
            assertEquals(2, alive.size());
            assertFalse(alive.contains(s1));
        }

        @Test
        @DisplayName("3.3 标记恢复后重新加入存活列表")
        void testMarkAlive() {
            Server s1 = serverList.getServers().get(0);
            serverList.markDown(s1);
            serverList.markAlive(s1);
            List<Server> alive = serverList.getAliveServers();
            assertEquals(3, alive.size());
        }

        @Test
        @DisplayName("3.4 next()优先选择存活Server")
        void testNextPreferAlive() {
            // 标记第一个和第二个宕机
            serverList.markDown(serverList.getServers().get(0));
            serverList.markDown(serverList.getServers().get(1));
            // next应该返回存活的第三个
            Server next = serverList.next();
            assertEquals("10.0.0.3", next.getHost());
            assertTrue(next.isAlive());
        }

        @Test
        @DisplayName("3.5 所有Server宕机时next()降级返回（给恢复机会）")
        void testNextDegradedWhenAllDown() {
            serverList.markDown(serverList.getServers().get(0));
            serverList.markDown(serverList.getServers().get(1));
            serverList.markDown(serverList.getServers().get(2));
            // 不应返回null，应降级轮询所有
            Server next = serverList.next();
            assertNotNull(next);
        }

        @Test
        @DisplayName("3.6 getDownServers返回宕机列表")
        void testGetDownServers() {
            serverList.markDown(serverList.getServers().get(0));
            List<Server> down = serverList.getDownServers();
            assertEquals(1, down.size());
            assertEquals("10.0.0.1", down.get(0).getHost());
        }
    }

    // ==================== 4. 并发安全测试 ====================

    @Nested
    @DisplayName("4. 并发安全")
    class Concurrency {

        @Test
        @DisplayName("4.1 多线程轮询不抛异常")
        void testConcurrentNext() throws InterruptedException {
            Thread[] threads = new Thread[10];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        Server s = serverList.next();
                        assertNotNull(s);
                    }
                });
                threads[i].start();
            }
            for (Thread t : threads) {
                t.join();
            }
        }
    }
}
