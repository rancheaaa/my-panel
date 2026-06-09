package com.cq.panel.admin.server.service.batch;

import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.mapper.AgentRegistryMapper;
import com.cq.panel.admin.server.repository.service.IAgentConnectivityService;
import com.cq.panel.admin.server.repository.service.impl.AgentConnectivityServiceImpl;
import com.cq.panel.admin.server.service.ProxyClientService;
import com.cq.panel.admin.server.web.domain.dto.proxy.*;
import com.cq.panel.admin.server.web.domain.vo.batch.AgentConnectivityVO;
import com.cq.panel.admin.server.web.domain.vo.batch.AgentConnectivityVO.PortProbeResult;
import com.cq.panel.admin.server.web.domain.vo.batch.AgentConnectivityVO.Status;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentConnectivityServiceTest {

    private AgentRegistryMapper agentRegistryMapper;
    private ProxyClientService proxyClientService;
    private IAgentConnectivityService connectivityService;

    @BeforeEach
    void setUp() {
        agentRegistryMapper = mock(AgentRegistryMapper.class);
        proxyClientService = mock(ProxyClientService.class);
        connectivityService = new AgentConnectivityServiceImpl(agentRegistryMapper, proxyClientService);
    }

    private AgentRegistry buildAgent(String id, String nodeName, String ip, int port, Integer nodeStatus, Integer nodeEnabled) {
        AgentRegistry agent = new AgentRegistry();
        agent.setId(id);
        agent.setNodeName(nodeName);
        agent.setAgentIp(ip);
        agent.setAgentPort(port);
        agent.setNodeStatus(nodeStatus);
        agent.setNodeEnabled(nodeEnabled);
        return agent;
    }

    private AgentConnectivityServiceImpl asImpl() {
        return (AgentConnectivityServiceImpl) connectivityService;
    }

    /** 构建所有网络探测都通过的spy（含Admin->Proxy检测） */
    private AgentConnectivityServiceImpl buildReachableSpy(String sourceId, String sourceIp, int sourcePort,
                                                             String targetId, String targetIp, int targetPort) {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent(sourceId, "source", sourceIp, sourcePort, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent(targetId, "target", targetIp, targetPort, 1, 0));

        // Mock ping → UP
        when(proxyClientService.ping()).thenReturn(ProxyApiResponse.<PingResult>builder()
                .code(200).msg("success").data(PingResult.builder().status("UP").timestamp(System.currentTimeMillis()).build()).build());

        // Mock tcpProbe → reachable
        TcpProbeResultData reachableData = TcpProbeResultData.builder().reachable(true).host(sourceIp).port(sourcePort).build();
        when(proxyClientService.tcpProbe(eq(sourceIp), anyInt()))
                .thenReturn(ProxyApiResponse.<TcpProbeResultData>builder().code(200).msg("success").data(reachableData).build());
        when(proxyClientService.tcpProbe(eq(targetIp), anyInt()))
                .thenReturn(ProxyApiResponse.<TcpProbeResultData>builder().code(200).msg("success").data(
                        TcpProbeResultData.builder().reachable(true).host(targetIp).port(targetPort).build()).build());

        // Mock probeViaAgent → reachable
        String agentProbeJson = "{\"success\":true,\"msg\":\"ok\",\"data\":{\"reachable\":true,\"details\":[\"✅ OK\"]}}";
        when(proxyClientService.probeViaAgent(anyString(), anyString(), anyInt()))
                .thenReturn(ProxyApiResponse.<String>builder().code(200).msg("success").data(agentProbeJson).build());
        when(proxyClientService.parseAgentResponse(anyString(), any()))
                .thenAnswer(inv -> {
                    String json = inv.getArgument(0);
                    return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                            new com.fasterxml.jackson.core.type.TypeReference<AgentResponse<AgentProbeData>>() {});
                });

        AgentConnectivityServiceImpl spy = Mockito.spy(asImpl());
        doReturn(PortProbeResult.of("admin", "proxy", "", 0, true, null))
                .when(spy).probeProxy();
        doReturn(PortProbeResult.of("proxy", "源Agent", sourceIp, sourcePort, true, null))
                .when(spy).probePort(eq("proxy"), eq("源Agent"), eq(sourceIp), eq(sourcePort));
        doReturn(PortProbeResult.of("proxy", "目标Agent", targetIp, targetPort, true, null))
                .when(spy).probePort(eq("proxy"), eq("目标Agent"), eq(targetIp), eq(targetPort));
        doReturn(PortProbeResult.of("源Agent", "目标Agent", targetIp, targetPort, true, null))
                .when(spy).probeViaAgent(sourceId, targetIp, targetPort);
        return spy;
    }

    // ==================== 1. 输入校验测试 ====================

    @Test
    @DisplayName("1.1 源节点名称为null时返回SOURCE_NOT_FOUND")
    void testSourceNull() {
        AgentConnectivityVO vo = connectivityService.checkConnectivity(null, "target");
        assertEquals(Status.SOURCE_NOT_FOUND.name(), vo.getConnectivityStatus());
        assertTrue(vo.getFailureReason().contains("源节点名称不能为空"));
    }

    @Test
    @DisplayName("1.2 源节点名称为空白时返回SOURCE_NOT_FOUND")
    void testSourceBlank() {
        AgentConnectivityVO vo = connectivityService.checkConnectivity("   ", "target");
        assertEquals(Status.SOURCE_NOT_FOUND.name(), vo.getConnectivityStatus());
    }

    @Test
    @DisplayName("1.3 目标节点名称为null时返回TARGET_NOT_FOUND")
    void testTargetNull() {
        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", null);
        assertEquals(Status.TARGET_NOT_FOUND.name(), vo.getConnectivityStatus());
        assertTrue(vo.getFailureReason().contains("目标节点名称不能为空"));
    }

    @Test
    @DisplayName("1.4 目标节点名称为空白时返回TARGET_NOT_FOUND")
    void testTargetBlank() {
        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "  ");
        assertEquals(Status.TARGET_NOT_FOUND.name(), vo.getConnectivityStatus());
    }

    // ==================== 2. 节点存在性检查 ====================

    @Test
    @DisplayName("2.1 源节点不存在返回SOURCE_NOT_FOUND")
    void testSourceNotFound() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(null);
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("t1", "target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_NOT_FOUND.name(), vo.getConnectivityStatus());
        assertFalse(vo.isSourceExists());
        assertTrue(vo.getFailureReason().contains("源节点不存在"));
    }

    @Test
    @DisplayName("2.2 目标节点不存在返回TARGET_NOT_FOUND")
    void testTargetNotFound() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("s1", "source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(null);

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.TARGET_NOT_FOUND.name(), vo.getConnectivityStatus());
        assertFalse(vo.isTargetExists());
        assertTrue(vo.getFailureReason().contains("目标节点不存在"));
    }

    // ==================== 3. 节点状态检查 ====================

    @Test
    @DisplayName("3.1 源节点离线返回SOURCE_OFFLINE")
    void testSourceOffline() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("s1", "source", "10.0.0.1", 7777, 0, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("t1", "target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_OFFLINE.name(), vo.getConnectivityStatus());
        assertFalse(vo.isSourceOnline());
    }

    @Test
    @DisplayName("3.2 源节点nodeStatus为null视为离线")
    void testSourceStatusNull() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("s1", "source", "10.0.0.1", 7777, null, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("t1", "target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_OFFLINE.name(), vo.getConnectivityStatus());
    }

    @Test
    @DisplayName("3.3 目标节点离线返回TARGET_OFFLINE")
    void testTargetOffline() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("s1", "source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("t1", "target", "10.0.0.2", 7777, 0, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.TARGET_OFFLINE.name(), vo.getConnectivityStatus());
        assertFalse(vo.isTargetOnline());
    }

    // ==================== 4. 节点启用状态检查 ====================

    @Test
    @DisplayName("4.1 源节点临时关闭返回SOURCE_DISABLED")
    void testSourceTempDisabled() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("s1", "source", "10.0.0.1", 7777, 1, 1));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("t1", "target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_DISABLED.name(), vo.getConnectivityStatus());
        assertFalse(vo.isSourceEnabled());
    }

    @Test
    @DisplayName("4.2 源节点永久关闭返回SOURCE_DISABLED")
    void testSourcePermDisabled() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("s1", "source", "10.0.0.1", 7777, 1, 2));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("t1", "target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_DISABLED.name(), vo.getConnectivityStatus());
    }

    @Test
    @DisplayName("4.3 源节点nodeEnabled为null视为禁用")
    void testSourceEnabledNull() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("s1", "source", "10.0.0.1", 7777, 1, null));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("t1", "target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_DISABLED.name(), vo.getConnectivityStatus());
    }

    @Test
    @DisplayName("4.4 目标节点禁用返回TARGET_DISABLED")
    void testTargetDisabled() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("s1", "source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("t1", "target", "10.0.0.2", 7777, 1, 2));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.TARGET_DISABLED.name(), vo.getConnectivityStatus());
        assertFalse(vo.isTargetEnabled());
    }

    // ==================== 5. 端口探测测试（通过Proxy） ====================

    @Test
    @DisplayName("5.1 probePort - Proxy返回可达")
    void testProbePortReachable() {
        TcpProbeResultData data = TcpProbeResultData.builder().reachable(true).host("10.0.0.1").port(7777).build();
        when(proxyClientService.tcpProbe("10.0.0.1", 7777))
                .thenReturn(ProxyApiResponse.<TcpProbeResultData>builder().code(200).msg("success").data(data).build());

        PortProbeResult result = asImpl().probePort("proxy", "源Agent", "10.0.0.1", 7777);
        assertTrue(result.isReachable());
        assertNull(result.getFailureReason());
    }

    @Test
    @DisplayName("5.2 probePort - Proxy返回不可达")
    void testProbePortUnreachable() {
        TcpProbeResultData data = TcpProbeResultData.builder().reachable(false).host("10.0.0.1").port(7777)
                .failureReason("连接被拒绝").build();
        when(proxyClientService.tcpProbe("10.0.0.1", 7777))
                .thenReturn(ProxyApiResponse.<TcpProbeResultData>builder().code(200).msg("success").data(data).build());

        PortProbeResult result = asImpl().probePort("proxy", "源Agent", "10.0.0.1", 7777);
        assertFalse(result.isReachable());
        assertTrue(result.getFailureReason().contains("连接被拒绝"));
    }

    @Test
    @DisplayName("5.3 probePort - Proxy请求异常")
    void testProbePortException() {
        when(proxyClientService.tcpProbe("10.0.0.1", 7777)).thenThrow(new RuntimeException("连接超时"));

        PortProbeResult result = asImpl().probePort("proxy", "源Agent", "10.0.0.1", 7777);
        assertFalse(result.isReachable());
        assertTrue(result.getFailureReason().contains("Proxy探测请求失败"));
    }

    // ==================== 5b. Admin->Proxy连通性测试 ====================

    @Test
    @DisplayName("5.4 probeProxy - Ping成功")
    void testProbeProxySuccess() {
        PingResult data = PingResult.builder().status("UP").timestamp(1234567890L).build();
        when(proxyClientService.ping())
                .thenReturn(ProxyApiResponse.<PingResult>builder().code(200).msg("success").data(data).build());

        PortProbeResult result = asImpl().probeProxy();
        assertTrue(result.isReachable());
        assertEquals("admin", result.getFrom());
        assertEquals("proxy", result.getTo());
        assertNull(result.getFailureReason());
    }

    @Test
    @DisplayName("5.5 probeProxy - Ping返回非UP状态")
    void testProbeProxyNotUp() {
        PingResult data = PingResult.builder().status("DOWN").build();
        when(proxyClientService.ping())
                .thenReturn(ProxyApiResponse.<PingResult>builder().code(200).msg("success").data(data).build());

        PortProbeResult result = asImpl().probeProxy();
        assertFalse(result.isReachable());
        assertTrue(result.getFailureReason().contains("状态异常"));
    }

    @Test
    @DisplayName("5.6 probeProxy - 连接失败")
    void testProbeProxyConnectionFailed() {
        when(proxyClientService.ping()).thenThrow(new RuntimeException("Connection refused: localhost:9876"));

        PortProbeResult result = asImpl().probeProxy();
        assertFalse(result.isReachable());
        assertTrue(result.getFailureReason().contains("Connection refused"));
    }

    // ==================== 6. 网络层探测集成测试 ====================

    @Test
    @DisplayName("6.1 Admin→Proxy不可达返回ADMIN_TO_PROXY_UNREACHABLE")
    void testAdminToProxyUnreachable() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("s1", "source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("t1", "target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityServiceImpl spy = Mockito.spy(asImpl());
        doReturn(PortProbeResult.of("admin", "proxy", "", 0, false, "连接超时"))
                .when(spy).probeProxy();

        AgentConnectivityVO vo = spy.checkConnectivity("source", "target");
        assertEquals(Status.ADMIN_TO_PROXY_UNREACHABLE.name(), vo.getConnectivityStatus());
        assertNotNull(vo.getAdminToProxy());
        assertFalse(vo.getAdminToProxy().isReachable());
    }

    @Test
    @DisplayName("6.2 Proxy→Source不可达返回ADMIN_TO_SOURCE_UNREACHABLE")
    void testProxyToSourceUnreachable() {
        AgentConnectivityServiceImpl spy = buildReachableSpy("s1", "10.0.0.1", 7777, "t1", "10.0.0.2", 7777);
        doReturn(PortProbeResult.of("proxy", "源Agent", "10.0.0.1", 7777, false, "连接被拒绝"))
                .when(spy).probePort("proxy", "源Agent", "10.0.0.1", 7777);

        AgentConnectivityVO vo = spy.checkConnectivity("source", "target");
        assertEquals(Status.ADMIN_TO_SOURCE_UNREACHABLE.name(), vo.getConnectivityStatus());
        assertNotNull(vo.getAdminToSource());
        assertFalse(vo.getAdminToSource().isReachable());
    }

    @Test
    @DisplayName("6.3 Proxy→Target不可达返回ADMIN_TO_TARGET_UNREACHABLE")
    void testProxyToTargetUnreachable() {
        AgentConnectivityServiceImpl spy = buildReachableSpy("s1", "10.0.0.1", 7777, "t1", "10.0.0.2", 7777);
        doReturn(PortProbeResult.of("proxy", "目标Agent", "10.0.0.2", 7777, false, "连接超时"))
                .when(spy).probePort("proxy", "目标Agent", "10.0.0.2", 7777);

        AgentConnectivityVO vo = spy.checkConnectivity("source", "target");
        assertEquals(Status.ADMIN_TO_TARGET_UNREACHABLE.name(), vo.getConnectivityStatus());
        assertNotNull(vo.getAdminToTarget());
        assertFalse(vo.getAdminToTarget().isReachable());
    }

    @Test
    @DisplayName("6.4 Source→Target不可达返回SOURCE_TO_TARGET_UNREACHABLE")
    void testSourceToTargetUnreachable() {
        AgentConnectivityServiceImpl spy = buildReachableSpy("s1", "10.0.0.1", 7777, "t1", "10.0.0.2", 7777);
        doReturn(PortProbeResult.of("源Agent", "目标Agent", "10.0.0.2", 7777, false, "网络不可达"))
                .when(spy).probeViaAgent("s1", "10.0.0.2", 7777);

        AgentConnectivityVO vo = spy.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_TO_TARGET_UNREACHABLE.name(), vo.getConnectivityStatus());
        assertNotNull(vo.getSourceToTarget());
        assertFalse(vo.getSourceToTarget().isReachable());
    }

    @Test
    @DisplayName("6.5 五层网络全部可达返回REACHABLE")
    void testAllReachable() {
        AgentConnectivityServiceImpl spy = buildReachableSpy("s1", "10.0.0.1", 7777, "t1", "10.0.0.2", 7777);

        AgentConnectivityVO vo = spy.checkConnectivity("source", "target");
        assertEquals(Status.REACHABLE.name(), vo.getConnectivityStatus());
        assertNull(vo.getFailureReason());
        assertTrue(vo.getAdminToProxy().isReachable());
        assertTrue(vo.getAdminToSource().isReachable());
        assertTrue(vo.getAdminToTarget().isReachable());
        assertTrue(vo.getSourceToTarget().isReachable());
    }

    // ==================== 7. checkDetails完整性测试 ====================

    @Test
    @DisplayName("7.1 全部可达时checkDetails包含所有✅步骤（6前置+4网络层）")
    void testCheckDetailsAllReachable() {
        AgentConnectivityServiceImpl spy = buildReachableSpy("s1", "10.0.0.1", 7777, "t1", "10.0.0.2", 7777);

        AgentConnectivityVO vo = spy.checkConnectivity("source", "target");
        // 6项前置检查 + 4项网络层(Admin->Proxy/Proxy->源Agent/Proxy->目标Agent/源Agent->目标Agent)
        // 反向探测(目标->源)在reverseCheckDetails中
        assertEquals(10, vo.getCheckDetails().size());
        assertTrue(vo.getCheckDetails().stream().allMatch(d -> d.startsWith("✅")));
    }

    @Test
    @DisplayName("7.2 源节点离线时checkDetails包含❌")
    void testCheckDetailsOffline() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("s1", "source", "10.0.0.1", 7777, 0, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("t1", "target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertTrue(vo.getCheckDetails().stream().anyMatch(d -> d.startsWith("❌")));
    }

    // ==================== 8. PortProbeResult测试 ====================

    @Test
    @DisplayName("8.1 PortProbeResult.of正确设置所有字段")
    void testPortProbeResultOf() {
        PortProbeResult r = PortProbeResult.of("admin", "proxy", "", 0, true, null);
        assertEquals("admin", r.getFrom());
        assertEquals("proxy", r.getTo());
        assertTrue(r.isReachable());
        assertNull(r.getFailureReason());
    }

    @Test
    @DisplayName("8.2 PortProbeResult不可达时failureReason非空")
    void testPortProbeResultUnreachable() {
        PortProbeResult r = PortProbeResult.of("proxy", "目标Agent", "10.0.0.2", 8888, false, "连接超时");
        assertFalse(r.isReachable());
        assertEquals("连接超时", r.getFailureReason());
    }

    // ==================== 9. VO字段完整性测试 ====================

    @Test
    @DisplayName("9.1 全部可达时VO所有字段正确填充（含adminToProxy）")
    void testVoAllFieldsPopulated() {
        AgentConnectivityServiceImpl spy = buildReachableSpy("s1", "10.0.0.1", 7777, "t1", "10.0.0.2", 8888);

        AgentConnectivityVO vo = spy.checkConnectivity("source", "target");

        assertEquals("source", vo.getSourceNodeName());
        assertEquals("target", vo.getTargetNodeName());
        assertEquals("10.0.0.1", vo.getSourceIp());
        assertEquals(7777, vo.getSourcePort());
        assertEquals("10.0.0.2", vo.getTargetIp());
        assertEquals(8888, vo.getTargetPort());
        assertTrue(vo.isSourceExists());
        assertTrue(vo.isTargetExists());
        assertTrue(vo.isSourceOnline());
        assertTrue(vo.isTargetOnline());
        assertTrue(vo.isSourceEnabled());
        assertTrue(vo.isTargetEnabled());
        assertNotNull(vo.getAdminToProxy());
        assertTrue(vo.getAdminToProxy().isReachable());
        assertEquals("admin", vo.getAdminToProxy().getFrom());
        assertEquals("proxy", vo.getAdminToProxy().getTo());
    }

    // ==================== 10. 边界条件测试 ====================

    @Test
    @DisplayName("10.1 源节点nodeStatus=2(未知)视为离线")
    void testSourceStatusUnknown() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("s1", "source", "10.0.0.1", 7777, 2, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("t1", "target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_OFFLINE.name(), vo.getConnectivityStatus());
    }

    @Test
    @DisplayName("10.2 目标节点nodeStatus=2(未知)视为离线")
    void testTargetStatusUnknown() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("s1", "source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("t1", "target", "10.0.0.2", 7777, 2, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.TARGET_OFFLINE.name(), vo.getConnectivityStatus());
    }

    @Test
    @DisplayName("10.3 源和目标节点名称相同也正常检测")
    void testSameSourceAndTarget() {
        when(agentRegistryMapper.selectByNodeName("node1")).thenReturn(buildAgent("n1", "node1", "10.0.0.1", 7777, 1, 0));

        AgentConnectivityServiceImpl spy = Mockito.spy(asImpl());
        doReturn(PortProbeResult.of("admin", "proxy", "", 0, true, null))
                .when(spy).probeProxy();
        doReturn(PortProbeResult.of("proxy", "源Agent", "10.0.0.1", 7777, true, null))
                .when(spy).probePort("proxy", "源Agent", "10.0.0.1", 7777);
        doReturn(PortProbeResult.of("proxy", "目标Agent", "10.0.0.1", 7777, true, null))
                .when(spy).probePort("proxy", "目标Agent", "10.0.0.1", 7777);
        doReturn(PortProbeResult.of("源Agent", "目标Agent", "10.0.0.1", 7777, true, null))
                .when(spy).probeViaAgent("n1", "10.0.0.1", 7777);

        AgentConnectivityVO vo = spy.checkConnectivity("node1", "node1");
        assertEquals(Status.REACHABLE.name(), vo.getConnectivityStatus());
    }

    @Test
    @DisplayName("10.4 probeViaAgent调用源Agent失败时返回不可达")
    void testProbeViaAgentException() {
        AgentConnectivityServiceImpl spy = buildReachableSpy("s1", "10.0.0.1", 7777, "t1", "10.0.0.2", 7777);
        doReturn(PortProbeResult.of("源Agent", "目标Agent", "10.0.0.2", 7777, false, "调用源Agent探测接口失败: Connection refused"))
                .when(spy).probeViaAgent("s1", "10.0.0.2", 7777);

        AgentConnectivityVO vo = spy.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_TO_TARGET_UNREACHABLE.name(), vo.getConnectivityStatus());
        assertTrue(vo.getFailureReason().contains("源Agent无法连接目标Agent"));
    }
}
