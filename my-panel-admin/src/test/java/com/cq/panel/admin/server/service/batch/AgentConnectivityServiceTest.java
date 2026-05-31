package com.cq.panel.admin.server.service.batch;

import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.mapper.AgentRegistryMapper;
import com.cq.panel.admin.server.repository.service.IAgentConnectivityService;
import com.cq.panel.admin.server.repository.service.impl.AgentConnectivityServiceImpl;
import com.cq.panel.admin.server.web.domain.vo.batch.AgentConnectivityVO;
import com.cq.panel.admin.server.web.domain.vo.batch.AgentConnectivityVO.PortProbeResult;
import com.cq.panel.admin.server.web.domain.vo.batch.AgentConnectivityVO.Status;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AgentConnectivityServiceTest {

    private AgentRegistryMapper agentRegistryMapper;
    private ObjectMapper objectMapper;
    private IAgentConnectivityService connectivityService;

    @BeforeEach
    void setUp() {
        agentRegistryMapper = mock(AgentRegistryMapper.class);
        objectMapper = new ObjectMapper();
        connectivityService = new AgentConnectivityServiceImpl(agentRegistryMapper, objectMapper);
    }

    private AgentRegistry buildAgent(String nodeName, String ip, int port, Integer nodeStatus, Integer nodeEnabled) {
        AgentRegistry agent = new AgentRegistry();
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
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_NOT_FOUND.name(), vo.getConnectivityStatus());
        assertFalse(vo.isSourceExists());
        assertTrue(vo.getFailureReason().contains("源节点不存在"));
    }

    @Test
    @DisplayName("2.2 目标节点不存在返回TARGET_NOT_FOUND")
    void testTargetNotFound() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 1, 0));
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
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 0, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_OFFLINE.name(), vo.getConnectivityStatus());
        assertFalse(vo.isSourceOnline());
    }

    @Test
    @DisplayName("3.2 源节点nodeStatus为null视为离线")
    void testSourceStatusNull() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, null, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_OFFLINE.name(), vo.getConnectivityStatus());
    }

    @Test
    @DisplayName("3.3 目标节点离线返回TARGET_OFFLINE")
    void testTargetOffline() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 0, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.TARGET_OFFLINE.name(), vo.getConnectivityStatus());
        assertFalse(vo.isTargetOnline());
    }

    // ==================== 4. 节点启用状态检查 ====================

    @Test
    @DisplayName("4.1 源节点临时关闭返回SOURCE_DISABLED")
    void testSourceTempDisabled() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 1, 1));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_DISABLED.name(), vo.getConnectivityStatus());
        assertFalse(vo.isSourceEnabled());
    }

    @Test
    @DisplayName("4.2 源节点永久关闭返回SOURCE_DISABLED")
    void testSourcePermDisabled() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 1, 2));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_DISABLED.name(), vo.getConnectivityStatus());
    }

    @Test
    @DisplayName("4.3 源节点nodeEnabled为null视为禁用")
    void testSourceEnabledNull() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 1, null));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_DISABLED.name(), vo.getConnectivityStatus());
    }

    @Test
    @DisplayName("4.4 目标节点禁用返回TARGET_DISABLED")
    void testTargetDisabled() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 2));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.TARGET_DISABLED.name(), vo.getConnectivityStatus());
        assertFalse(vo.isTargetEnabled());
    }

    // ==================== 5. 端口探测测试 ====================

    @Test
    @DisplayName("5.1 classifyConnectionFailure - Connection refused")
    void testClassifyConnectionRefused() {
        IOException e = new IOException("Connection refused");
        String result = asImpl().classifyConnectionFailure(e);
        assertTrue(result.contains("连接被拒绝"));
    }

    @Test
    @DisplayName("5.2 classifyConnectionFailure - Network unreachable")
    void testClassifyNetworkUnreachable() {
        IOException e = new IOException("Network is unreachable");
        String result = asImpl().classifyConnectionFailure(e);
        assertTrue(result.contains("网络不可达"));
    }

    @Test
    @DisplayName("5.3 classifyConnectionFailure - No route to host")
    void testClassifyNoRoute() {
        IOException e = new IOException("No route to host");
        String result = asImpl().classifyConnectionFailure(e);
        assertTrue(result.contains("网络不可达"));
    }

    @Test
    @DisplayName("5.4 classifyConnectionFailure - Connection timed out")
    void testClassifyTimeout() {
        IOException e = new IOException("Connection timed out");
        String result = asImpl().classifyConnectionFailure(e);
        assertTrue(result.contains("连接超时"));
    }

    @Test
    @DisplayName("5.5 classifyConnectionFailure - Connection reset")
    void testClassifyReset() {
        IOException e = new IOException("Connection reset");
        String result = asImpl().classifyConnectionFailure(e);
        assertTrue(result.contains("连接被重置"));
    }

    @Test
    @DisplayName("5.6 classifyConnectionFailure - Permission denied")
    void testClassifyPermission() {
        IOException e = new IOException("Permission denied");
        String result = asImpl().classifyConnectionFailure(e);
        assertTrue(result.contains("权限被拒绝"));
    }

    @Test
    @DisplayName("5.7 classifyConnectionFailure - null message")
    void testClassifyNullMessage() {
        IOException e = new IOException((String) null);
        String result = asImpl().classifyConnectionFailure(e);
        assertEquals("未知原因", result);
    }

    @Test
    @DisplayName("5.8 classifyConnectionFailure - unknown error")
    void testClassifyUnknown() {
        IOException e = new IOException("Some weird error");
        String result = asImpl().classifyConnectionFailure(e);
        assertTrue(result.contains("未知原因"));
        assertTrue(result.contains("Some weird error"));
    }

    // ==================== 6. 三层探测集成测试（使用spy mock probePort和probeViaAgent） ====================

    @Test
    @DisplayName("6.1 Admin→Source不可达返回ADMIN_TO_SOURCE_UNREACHABLE")
    void testAdminToSourceUnreachable() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityServiceImpl spy = Mockito.spy(asImpl());
        doReturn(PortProbeResult.of("admin", "源Agent", "10.0.0.1", 7777, false, "连接被拒绝"))
                .when(spy).probePort("admin", "源Agent", "10.0.0.1", 7777);

        AgentConnectivityVO vo = spy.checkConnectivity("source", "target");
        assertEquals(Status.ADMIN_TO_SOURCE_UNREACHABLE.name(), vo.getConnectivityStatus());
        assertNotNull(vo.getAdminToSource());
        assertFalse(vo.getAdminToSource().isReachable());
    }

    @Test
    @DisplayName("6.2 Admin→Target不可达返回ADMIN_TO_TARGET_UNREACHABLE")
    void testAdminToTargetUnreachable() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityServiceImpl spy = Mockito.spy(asImpl());
        doReturn(PortProbeResult.of("admin", "源Agent", "10.0.0.1", 7777, true, null))
                .when(spy).probePort("admin", "源Agent", "10.0.0.1", 7777);
        doReturn(PortProbeResult.of("admin", "目标Agent", "10.0.0.2", 7777, false, "连接超时"))
                .when(spy).probePort("admin", "目标Agent", "10.0.0.2", 7777);

        AgentConnectivityVO vo = spy.checkConnectivity("source", "target");
        assertEquals(Status.ADMIN_TO_TARGET_UNREACHABLE.name(), vo.getConnectivityStatus());
        assertNotNull(vo.getAdminToTarget());
        assertFalse(vo.getAdminToTarget().isReachable());
    }

    @Test
    @DisplayName("6.3 Source→Target不可达返回SOURCE_TO_TARGET_UNREACHABLE")
    void testSourceToTargetUnreachable() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityServiceImpl spy = Mockito.spy(asImpl());
        doReturn(PortProbeResult.of("admin", "源Agent", "10.0.0.1", 7777, true, null))
                .when(spy).probePort("admin", "源Agent", "10.0.0.1", 7777);
        doReturn(PortProbeResult.of("admin", "目标Agent", "10.0.0.2", 7777, true, null))
                .when(spy).probePort("admin", "目标Agent", "10.0.0.2", 7777);
        doReturn(PortProbeResult.of("源Agent", "目标Agent", "10.0.0.2", 7777, false, "网络不可达"))
                .when(spy).probeViaAgent("10.0.0.1", 7777, "10.0.0.2", 7777);

        AgentConnectivityVO vo = spy.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_TO_TARGET_UNREACHABLE.name(), vo.getConnectivityStatus());
        assertNotNull(vo.getSourceToTarget());
        assertFalse(vo.getSourceToTarget().isReachable());
    }

    @Test
    @DisplayName("6.4 三层全部可达返回REACHABLE")
    void testAllReachable() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityServiceImpl spy = Mockito.spy(asImpl());
        doReturn(PortProbeResult.of("admin", "源Agent", "10.0.0.1", 7777, true, null))
                .when(spy).probePort("admin", "源Agent", "10.0.0.1", 7777);
        doReturn(PortProbeResult.of("admin", "目标Agent", "10.0.0.2", 7777, true, null))
                .when(spy).probePort("admin", "目标Agent", "10.0.0.2", 7777);
        doReturn(PortProbeResult.of("源Agent", "目标Agent", "10.0.0.2", 7777, true, null))
                .when(spy).probeViaAgent("10.0.0.1", 7777, "10.0.0.2", 7777);

        AgentConnectivityVO vo = spy.checkConnectivity("source", "target");
        assertEquals(Status.REACHABLE.name(), vo.getConnectivityStatus());
        assertNull(vo.getFailureReason());
        assertTrue(vo.getAdminToSource().isReachable());
        assertTrue(vo.getAdminToTarget().isReachable());
        assertTrue(vo.getSourceToTarget().isReachable());
    }

    // ==================== 7. checkDetails完整性测试 ====================

    @Test
    @DisplayName("7.1 全部可达时checkDetails包含所有✅步骤")
    void testCheckDetailsAllReachable() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityServiceImpl spy = Mockito.spy(asImpl());
        doReturn(PortProbeResult.of("admin", "源Agent", "10.0.0.1", 7777, true, null))
                .when(spy).probePort("admin", "源Agent", "10.0.0.1", 7777);
        doReturn(PortProbeResult.of("admin", "目标Agent", "10.0.0.2", 7777, true, null))
                .when(spy).probePort("admin", "目标Agent", "10.0.0.2", 7777);
        doReturn(PortProbeResult.of("源Agent", "目标Agent", "10.0.0.2", 7777, true, null))
                .when(spy).probeViaAgent("10.0.0.1", 7777, "10.0.0.2", 7777);

        AgentConnectivityVO vo = spy.checkConnectivity("source", "target");
        assertEquals(9, vo.getCheckDetails().size());
        assertTrue(vo.getCheckDetails().stream().allMatch(d -> d.startsWith("✅")));
    }

    @Test
    @DisplayName("7.2 源节点离线时checkDetails包含❌")
    void testCheckDetailsOffline() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 0, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertTrue(vo.getCheckDetails().stream().anyMatch(d -> d.startsWith("❌")));
    }

    // ==================== 8. PortProbeResult测试 ====================

    @Test
    @DisplayName("8.1 PortProbeResult.of正确设置所有字段")
    void testPortProbeResultOf() {
        PortProbeResult r = PortProbeResult.of("admin", "源Agent", "10.0.0.1", 7777, true, null);
        assertEquals("admin", r.getFrom());
        assertEquals("源Agent", r.getTo());
        assertEquals("10.0.0.1", r.getTargetIp());
        assertEquals(7777, r.getTargetPort());
        assertTrue(r.isReachable());
        assertNull(r.getFailureReason());
    }

    @Test
    @DisplayName("8.2 PortProbeResult不可达时failureReason非空")
    void testPortProbeResultUnreachable() {
        PortProbeResult r = PortProbeResult.of("admin", "目标Agent", "10.0.0.2", 7777, false, "连接超时");
        assertFalse(r.isReachable());
        assertEquals("连接超时", r.getFailureReason());
    }

    // ==================== 9. VO字段完整性测试 ====================

    @Test
    @DisplayName("9.1 全部可达时VO所有字段正确填充")
    void testVoAllFieldsPopulated() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 8888, 1, 0));

        AgentConnectivityServiceImpl spy = Mockito.spy(asImpl());
        doReturn(PortProbeResult.of("admin", "源Agent", "10.0.0.1", 7777, true, null))
                .when(spy).probePort("admin", "源Agent", "10.0.0.1", 7777);
        doReturn(PortProbeResult.of("admin", "目标Agent", "10.0.0.2", 8888, true, null))
                .when(spy).probePort("admin", "目标Agent", "10.0.0.2", 8888);
        doReturn(PortProbeResult.of("源Agent", "目标Agent", "10.0.0.2", 8888, true, null))
                .when(spy).probeViaAgent("10.0.0.1", 7777, "10.0.0.2", 8888);

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
    }

    // ==================== 10. 边界条件测试 ====================

    @Test
    @DisplayName("10.1 源节点nodeStatus=2(未知)视为离线")
    void testSourceStatusUnknown() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 2, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_OFFLINE.name(), vo.getConnectivityStatus());
    }

    @Test
    @DisplayName("10.2 目标节点nodeStatus=2(未知)视为离线")
    void testTargetStatusUnknown() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 2, 0));

        AgentConnectivityVO vo = connectivityService.checkConnectivity("source", "target");
        assertEquals(Status.TARGET_OFFLINE.name(), vo.getConnectivityStatus());
    }

    @Test
    @DisplayName("10.3 源和目标节点名称相同也正常检测")
    void testSameSourceAndTarget() {
        when(agentRegistryMapper.selectByNodeName("node1")).thenReturn(buildAgent("node1", "10.0.0.1", 7777, 1, 0));

        AgentConnectivityServiceImpl spy = Mockito.spy(asImpl());
        doReturn(PortProbeResult.of("admin", "源Agent", "10.0.0.1", 7777, true, null))
                .when(spy).probePort("admin", "源Agent", "10.0.0.1", 7777);
        doReturn(PortProbeResult.of("admin", "目标Agent", "10.0.0.1", 7777, true, null))
                .when(spy).probePort("admin", "目标Agent", "10.0.0.1", 7777);
        doReturn(PortProbeResult.of("源Agent", "目标Agent", "10.0.0.1", 7777, true, null))
                .when(spy).probeViaAgent("10.0.0.1", 7777, "10.0.0.1", 7777);

        AgentConnectivityVO vo = spy.checkConnectivity("node1", "node1");
        assertEquals(Status.REACHABLE.name(), vo.getConnectivityStatus());
    }

    @Test
    @DisplayName("10.4 probeViaAgent调用源Agent失败时返回不可达")
    void testProbeViaAgentException() {
        when(agentRegistryMapper.selectByNodeName("source")).thenReturn(buildAgent("source", "10.0.0.1", 7777, 1, 0));
        when(agentRegistryMapper.selectByNodeName("target")).thenReturn(buildAgent("target", "10.0.0.2", 7777, 1, 0));

        AgentConnectivityServiceImpl spy = Mockito.spy(asImpl());
        doReturn(PortProbeResult.of("admin", "源Agent", "10.0.0.1", 7777, true, null))
                .when(spy).probePort("admin", "源Agent", "10.0.0.1", 7777);
        doReturn(PortProbeResult.of("admin", "目标Agent", "10.0.0.2", 7777, true, null))
                .when(spy).probePort("admin", "目标Agent", "10.0.0.2", 7777);
        doReturn(PortProbeResult.of("源Agent", "目标Agent", "10.0.0.2", 7777, false, "调用源Agent探测接口失败: Connection refused"))
                .when(spy).probeViaAgent("10.0.0.1", 7777, "10.0.0.2", 7777);

        AgentConnectivityVO vo = spy.checkConnectivity("source", "target");
        assertEquals(Status.SOURCE_TO_TARGET_UNREACHABLE.name(), vo.getConnectivityStatus());
        assertTrue(vo.getFailureReason().contains("源Agent无法连接目标Agent"));
    }
}
