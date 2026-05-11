package com.cq.agent.handler.batch;

import com.cq.agent.batch.config.BatchTransferTaskConfig;
import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BatchConfigReceiveHandler 规格测试
 * 验证Agent接收Proxy推送配置并返回正确格式
 * 符合spec.md设计要求
 */
@DisplayName("配置接收Handler - 规格验证")
class BatchConfigReceiveHandlerSpecTest {

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

    // ==================== 1. 返回格式验证 ====================

    @Test
    @DisplayName("1. 成功响应 - 必须包含configPersisted=true")
    void testSuccessResponse_containsConfigPersisted() {
        when(configChangeListener.detectAndApplyChange(any())).thenReturn(true);

        EmbeddedChannel channel = new EmbeddedChannel();
        ChannelHandlerContext ctx = mockContext(channel);
        FullHttpRequest request = createPostRequest(configJson());

        handler.handle(ctx, request);

        // 验证响应包含configPersisted
        String responseBody = getResponseBody(channel);
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

        assertTrue(json.get("success").getAsBoolean(), "success应为true");
        assertTrue(json.getAsJsonObject("data").get("configPersisted").getAsBoolean(),
            "data.configPersisted应为true");
    }

    @Test
    @DisplayName("2. 成功响应 - 必须包含receivedAt时间戳")
    void testSuccessResponse_containsReceivedAt() {
        when(configChangeListener.detectAndApplyChange(any())).thenReturn(true);

        EmbeddedChannel channel = new EmbeddedChannel();
        ChannelHandlerContext ctx = mockContext(channel);
        FullHttpRequest request = createPostRequest(configJson());

        handler.handle(ctx, request);

        String responseBody = getResponseBody(channel);
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonObject data = json.getAsJsonObject("data");

        assertNotNull(data.get("receivedAt"), "data.receivedAt不应为null");
        assertTrue(data.get("receivedAt").getAsLong() > 0, "receivedAt应为有效时间戳");
    }

    @Test
    @DisplayName("3. 成功响应 - 必须包含version字段")
    void testSuccessResponse_containsVersion() {
        when(configChangeListener.detectAndApplyChange(any())).thenReturn(true);

        EmbeddedChannel channel = new EmbeddedChannel();
        ChannelHandlerContext ctx = mockContext(channel);
        FullHttpRequest request = createPostRequest(configJson());

        handler.handle(ctx, request);

        String responseBody = getResponseBody(channel);
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonObject data = json.getAsJsonObject("data");

        assertNotNull(data.get("version"), "data.version不应为null");
        assertEquals(20260509103000L, data.get("version").getAsLong(), "version应正确返回");
    }

    // ==================== 2. 幂等性验证 ====================

    @Test
    @DisplayName("4. 相同version重复接收 - 仍返回configPersisted=true")
    void testIdempotent_sameVersion_returnsSuccess() {
        // 模拟版本管理器返回false（版本相同），但handler仍应返回success
        when(configChangeListener.detectAndApplyChange(any())).thenReturn(false);

        EmbeddedChannel channel = new EmbeddedChannel();
        ChannelHandlerContext ctx = mockContext(channel);
        FullHttpRequest request = createPostRequest(configJson());

        handler.handle(ctx, request);

        String responseBody = getResponseBody(channel);
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonObject data = json.getAsJsonObject("data");

        assertTrue(data.get("configPersisted").getAsBoolean(),
            "幂等接收也应返回configPersisted=true");
        assertFalse(data.get("changed").getAsBoolean(),
            "changed应为false");
    }

    // ==================== 3. 持久化失败场景 ====================

    @Test
    @DisplayName("5. 持久化失败 - 返回configPersisted=false")
    void testPersistFailed_returnsConfigPersistedFalse() {
        when(configChangeListener.detectAndApplyChange(any()))
            .thenThrow(new RuntimeException("磁盘已满"));

        EmbeddedChannel channel = new EmbeddedChannel();
        ChannelHandlerContext ctx = mockContext(channel);
        FullHttpRequest request = createPostRequest(configJson());

        handler.handle(ctx, request);

        String responseBody = getResponseBody(channel);
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

        assertFalse(json.get("success").getAsBoolean(), "success应为false");
    }

    // ==================== 4. 响应格式结构 ====================

    @Test
    @DisplayName("6. 响应结构 - 符合spec.md标准格式")
    void testResponseStructure_matchesSpec() {
        when(configChangeListener.detectAndApplyChange(any())).thenReturn(true);

        EmbeddedChannel channel = new EmbeddedChannel();
        ChannelHandlerContext ctx = mockContext(channel);
        FullHttpRequest request = createPostRequest(configJson());

        handler.handle(ctx, request);

        String responseBody = getResponseBody(channel);
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

        // 验证顶层结构
        assertTrue(json.has("success"), "必须有success字段");
        assertTrue(json.has("data"), "必须有data字段");

        // 验证data结构
        JsonObject data = json.getAsJsonObject("data");
        assertTrue(data.has("configPersisted"), "data必须有configPersisted");
        assertTrue(data.has("receivedAt"), "data必须有receivedAt");
        assertTrue(data.has("version"), "data必须有version");
    }

    // ==================== 辅助方法 ====================

    private String configJson() {
        BatchTransferTaskConfig config = new BatchTransferTaskConfig();
        config.setTaskId(1L);
        config.setTaskName("日志备份任务");
        config.setSourceAgentId("agent-001");
        config.setSourceDir("/var/log/app");
        config.setTargetDirs(List.of("/backup/logs"));
        config.setIncludePatterns(List.of("*.log"));
        config.setExcludePatterns(List.of("debug*"));
        config.setTargetAgentIds(List.of("agent-002"));
        config.setTargetAgentNames(List.of("root@node2:7777"));
        config.setStatus("RUNNING");
        config.setVersion(20260509103000L);
        config.setMaxRetries(10);
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

    private ChannelHandlerContext mockContext(EmbeddedChannel channel) {
        ChannelFuture future = mock(ChannelFuture.class);
        when(future.addListener(any())).thenReturn(future);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.writeAndFlush(any())).thenAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            channel.writeOutbound(arg);
            return future;
        });
        return ctx;
    }

    private String getResponseBody(EmbeddedChannel channel) {
        Object msg = channel.readOutbound();
        if (msg instanceof FullHttpResponse response) {
            String body = response.content().toString(StandardCharsets.UTF_8);
            // 释放ByteBuf避免内存泄漏
            response.release();
            return body;
        }
        return "";
    }
}
