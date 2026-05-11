package com.cq.proxy.service.batch;

import com.cq.proxy.repository.entity.AgentRegistry;
import com.cq.proxy.repository.mapper.AgentRegistryMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AgentPushService 单元测试
 * 验证Proxy推送配置到Agent的功能
 * 符合spec.md设计要求
 */
class AgentPushServiceTest {

    @Mock
    private AgentRegistryMapper agentRegistryMapper;

    @InjectMocks
    private AgentPushService agentPushService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ==================== getAgentAddress ====================

    @Test
    @DisplayName("1. 根据agentId查询Agent地址 - 成功")
    void testGetAgentAddress_success() {
        AgentRegistry registry = new AgentRegistry();
        registry.setId("agent-001");
        registry.setAgentIp("10.240.85.177");
        registry.setAgentPort(7777);
        registry.setNodeStatus(1);

        when(agentRegistryMapper.selectByAgentId("agent-001")).thenReturn(registry);

        AgentPushService.AgentAddress address = agentPushService.getAgentAddress("agent-001");

        assertNotNull(address);
        assertEquals("10.240.85.177", address.getIp());
        assertEquals(7777, address.getPort());
    }

    @Test
    @DisplayName("2. 根据agentId查询Agent地址 - Agent不存在")
    void testGetAgentAddress_notFound() {
        when(agentRegistryMapper.selectByAgentId("agent-999")).thenReturn(null);

        AgentPushService.AgentAddress address = agentPushService.getAgentAddress("agent-999");

        assertNull(address);
    }

    @Test
    @DisplayName("3. 根据agentId查询Agent地址 - Agent离线")
    void testGetAgentAddress_offline() {
        AgentRegistry registry = new AgentRegistry();
        registry.setId("agent-001");
        registry.setAgentIp("10.240.85.177");
        registry.setAgentPort(7777);
        registry.setNodeStatus(0); // 离线

        when(agentRegistryMapper.selectByAgentId("agent-001")).thenReturn(registry);

        AgentPushService.AgentAddress address = agentPushService.getAgentAddress("agent-001");

        assertNull(address);
    }

    @Test
    @DisplayName("4. 构建Agent配置推送URL")
    void testBuildAgentUrl() {
        AgentPushService.AgentAddress address = new AgentPushService.AgentAddress("10.240.85.177", 7777);

        String url = agentPushService.buildAgentUrl(address, "/api/batch/task/config");

        assertEquals("http://10.240.85.177:7777/api/batch/task/config", url);
    }

    @Test
    @DisplayName("5. 构建Agent控制推送URL")
    void testBuildAgentControlUrl() {
        AgentPushService.AgentAddress address = new AgentPushService.AgentAddress("10.240.85.177", 7777);

        String url = agentPushService.buildAgentUrl(address, "/api/batch/task/control");

        assertEquals("http://10.240.85.177:7777/api/batch/task/control", url);
    }
}
