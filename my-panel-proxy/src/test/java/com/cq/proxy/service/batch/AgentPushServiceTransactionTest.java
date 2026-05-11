package com.cq.proxy.service.batch;

import com.cq.panel.common.loadbalancer.HttpResponse;
import com.cq.panel.common.loadbalancer.SimpleHttpClient;
import com.cq.proxy.repository.entity.AgentRegistry;
import com.cq.proxy.repository.mapper.AgentRegistryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AgentPushService 事务一致性测试
 * 验证：
 * 1. Agent返回configPersisted=true时才返回true
 * 2. Agent返回configPersisted=false时返回false
 * 3. Agent返回异常时抛出异常
 * 4. 正确解析JSON响应而非字符串匹配
 */
@DisplayName("AgentPushService - 事务一致性验证")
class AgentPushServiceTransactionTest {

    @Mock
    private AgentRegistryMapper agentRegistryMapper;

    @Mock
    private SimpleHttpClient httpClient;

    private ObjectMapper objectMapper;
    private AgentPushService agentPushService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        agentPushService = new AgentPushService(agentRegistryMapper, httpClient, objectMapper);

        // 默认Agent在线
        AgentRegistry registry = new AgentRegistry();
        registry.setId("agent-001");
        registry.setAgentIp("10.240.85.177");
        registry.setAgentPort(7777);
        registry.setNodeStatus(1);
        when(agentRegistryMapper.selectByAgentId("agent-001")).thenReturn(registry);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ==================== 1. Agent确认持久化成功 ====================

    @Test
    @DisplayName("1. Agent返回success=true且configPersisted=true - 返回true")
    void testPushConfig_agentConfirmPersisted_returnsTrue() {
        String responseJson = "{\"success\":true,\"data\":{\"configPersisted\":true,\"receivedAt\":1778430000000,\"version\":20260509103000}}";
        HttpResponse<String> response = new HttpResponse<>(responseJson, 200);
        doReturn(response).when(httpClient).post(anyString(), any(), anyMap(), any());

        boolean result = agentPushService.pushConfigToAgent("agent-001", "{\"taskId\":1}");

        assertTrue(result, "Agent确认持久化后应返回true");
    }

    @Test
    @DisplayName("2. Agent返回success=true但configPersisted=false - 返回false")
    void testPushConfig_agentNotPersisted_returnsFalse() {
        String responseJson = "{\"success\":true,\"data\":{\"configPersisted\":false,\"receivedAt\":1778430000000,\"version\":20260509103000}}";
        HttpResponse<String> response = new HttpResponse<>(responseJson, 200);
        doReturn(response).when(httpClient).post(anyString(), any(), anyMap(), any());

        boolean result = agentPushService.pushConfigToAgent("agent-001", "{\"taskId\":1}");

        assertFalse(result, "Agent未确认持久化时应返回false");
    }

    @Test
    @DisplayName("3. Agent返回success=false - 返回false")
    void testPushConfig_agentReturnsError_returnsFalse() {
        String responseJson = "{\"success\":false,\"code\":500,\"msg\":\"磁盘已满\"}";
        HttpResponse<String> response = new HttpResponse<>(responseJson, 200);
        doReturn(response).when(httpClient).post(anyString(), any(), anyMap(), any());

        boolean result = agentPushService.pushConfigToAgent("agent-001", "{\"taskId\":1}");

        assertFalse(result, "Agent返回失败时应返回false");
    }

    @Test
    @DisplayName("4. HTTP状态码非2xx - 返回false")
    void testPushConfig_httpError_returnsFalse() {
        HttpResponse<String> response = new HttpResponse<>("Internal Server Error", 500);
        doReturn(response).when(httpClient).post(anyString(), any(), anyMap(), any());

        boolean result = agentPushService.pushConfigToAgent("agent-001", "{\"taskId\":1}");

        assertFalse(result, "HTTP错误时应返回false");
    }

    @Test
    @DisplayName("5. 网络异常 - 抛出RuntimeException")
    void testPushConfig_networkException_throwsException() {
        doThrow(new RuntimeException("Connection timeout"))
            .when(httpClient).post(anyString(), any(), anyMap(), any());

        assertThrows(RuntimeException.class, () -> {
            agentPushService.pushConfigToAgent("agent-001", "{\"taskId\":1}");
        });
    }

    // ==================== 2. 删除指令推送 ====================

    @Test
    @DisplayName("6. 删除指令推送成功 - 返回true")
    void testPushDelete_success_returnsTrue() {
        String responseJson = "{\"success\":true,\"data\":{\"deleted\":true}}";
        HttpResponse<String> response = new HttpResponse<>(responseJson, 200);
        doReturn(response).when(httpClient).post(anyString(), any(), anyMap(), any());

        boolean result = agentPushService.pushDeleteToAgent("agent-001", "{\"taskId\":1}");

        assertTrue(result, "删除指令推送成功应返回true");
    }

    @Test
    @DisplayName("7. 删除指令推送失败 - 返回false")
    void testPushDelete_failure_returnsFalse() {
        String responseJson = "{\"success\":false,\"msg\":\"任务不存在\"}";
        HttpResponse<String> response = new HttpResponse<>(responseJson, 200);
        doReturn(response).when(httpClient).post(anyString(), any(), anyMap(), any());

        boolean result = agentPushService.pushDeleteToAgent("agent-001", "{\"taskId\":1}");

        assertFalse(result, "删除指令推送失败应返回false");
    }

    // ==================== 3. JSON解析验证 ====================

    @Test
    @DisplayName("8. 正确解析嵌套JSON中的configPersisted字段")
    void testPushConfig_parseNestedJson_correctly() {
        String responseJson = "{\"success\":true,\"code\":200,\"msg\":\"Success\",\"data\":{\"configPersisted\":true,\"receivedAt\":1778430000000,\"version\":20260509103000,\"taskId\":1,\"changed\":true}}";
        HttpResponse<String> response = new HttpResponse<>(responseJson, 200);
        doReturn(response).when(httpClient).post(anyString(), any(), anyMap(), any());

        boolean result = agentPushService.pushConfigToAgent("agent-001", "{\"taskId\":1}");

        assertTrue(result, "应正确解析嵌套JSON中的configPersisted");
    }

    @Test
    @DisplayName("9. 响应JSON格式异常 - 返回false")
    void testPushConfig_invalidJson_returnsFalse() {
        String responseJson = "not valid json";
        HttpResponse<String> response = new HttpResponse<>(responseJson, 200);
        doReturn(response).when(httpClient).post(anyString(), any(), anyMap(), any());

        boolean result = agentPushService.pushConfigToAgent("agent-001", "{\"taskId\":1}");

        assertFalse(result, "JSON格式异常时应返回false");
    }
}
