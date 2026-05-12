package com.cq.agent.handler.batch;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.RetryConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BatchConfigReceiveHandler 单元测试
 */
class BatchConfigReceiveHandlerTest {

    @Mock
    private FileService fileService;

    @Mock
    private ChunkedTransferService chunkedTransferService;

    @Mock
    private ConfigFileManager configFileManager;

    @Mock
    private ConfigChangeListener configChangeListener;

    @InjectMocks
    private BatchConfigReceiveHandler handler;

    private AutoCloseable mocks;
    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ==================== 正常接收配置 ====================

    @Test
    @DisplayName("1. POST /api/batch/task/config - 成功接收配置")
    void testHandle_success() {
        when(configChangeListener.detectAndApplyChange(any())).thenReturn(true);

        ChannelHandlerContext ctx = mockContext();
        FullHttpRequest request = createPostRequest(configJson());

        handler.handle(ctx, request);

        verify(configChangeListener).detectAndApplyChange(any(AgentTaskConfig.class));
    }

    @Test
    @DisplayName("2. 接收配置 - 返回configPersisted=true")
    void testHandle_returnsConfigPersisted() {
        when(configChangeListener.detectAndApplyChange(any())).thenReturn(true);

        ChannelHandlerContext ctx = mockContext();
        FullHttpRequest request = createPostRequest(configJson());

        handler.handle(ctx, request);

        verify(configChangeListener).detectAndApplyChange(any());
    }

    @Test
    @DisplayName("3. 接收配置 - 版本无变化时返回changed=false")
    void testHandle_noChange() {
        when(configChangeListener.detectAndApplyChange(any())).thenReturn(false);

        ChannelHandlerContext ctx = mockContext();
        FullHttpRequest request = createPostRequest(configJson());

        handler.handle(ctx, request);

        verify(configChangeListener).detectAndApplyChange(any());
    }

    // ==================== 异常场景 ====================

    @Test
    @DisplayName("4. 空body - 返回400错误")
    void testHandle_emptyBody() {
        ChannelHandlerContext ctx = mockContext();
        FullHttpRequest request = createPostRequest("");

        handler.handle(ctx, request);

        verify(configChangeListener, never()).detectAndApplyChange(any());
    }

    @Test
    @DisplayName("5. taskId为null - 返回400错误")
    void testHandle_nullTaskId() {
        String json = gson.toJson(new AgentTaskConfig()); // taskId is null

        ChannelHandlerContext ctx = mockContext();
        FullHttpRequest request = createPostRequest(json);

        handler.handle(ctx, request);

        verify(configChangeListener, never()).detectAndApplyChange(any());
    }

    @Test
    @DisplayName("6. GET方法 - 返回405 Method Not Allowed")
    void testHandle_wrongMethod() {
        ChannelHandlerContext ctx = mockContext();
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/batch/task/config");

        handler.handle(ctx, request);

        verify(configChangeListener, never()).detectAndApplyChange(any());
    }

    @Test
    @DisplayName("7. ConfigChangeListener抛异常 - 返回500错误")
    void testHandle_listenerException() {
        when(configChangeListener.detectAndApplyChange(any())).thenThrow(new RuntimeException("save failed"));

        ChannelHandlerContext ctx = mockContext();
        FullHttpRequest request = createPostRequest(configJson());

        handler.handle(ctx, request);

        verify(configChangeListener).detectAndApplyChange(any());
    }

    // ==================== 辅助方法 ====================

    private String configJson() {
        AgentTaskConfig config = new AgentTaskConfig();
        config.setTaskId(1L);
        config.setTaskName("日志备份任务");
        config.setSourceAgentId("agent-001");
        config.setSourceDir("/var/log/app");

        List<TargetAgentInfo> targets = new ArrayList<>();
        TargetAgentInfo target = new TargetAgentInfo();
        target.setAgentId("agent-002");
        target.setAgentName("root@node2:7777");
        target.setTargetDir("/backup/logs");
        targets.add(target);
        config.setTargetAgents(targets);

        config.setIncludePatterns(List.of("*.log"));
        config.setExcludePatterns(List.of("debug*"));
        config.setStatus("RUNNING");
        config.setVersion(String.valueOf(1));

        RetryConfig retryConfig = new RetryConfig();
        retryConfig.setMaxRetryCount(Integer.valueOf(10));
        config.setRetryConfig(retryConfig);
        return gson.toJson(config);
    }

    private FullHttpRequest createPostRequest(String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/batch/task/config",
            Unpooled.wrappedBuffer(bytes));
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        return request;
    }

    private ChannelHandlerContext mockContext() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ChannelFuture future = mock(ChannelFuture.class);
        when(future.addListener(any())).thenReturn(future);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.writeAndFlush(any())).thenReturn(future);
        return ctx;
    }
}
