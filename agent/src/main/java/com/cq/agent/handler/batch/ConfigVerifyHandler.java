package com.cq.agent.handler.batch;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.handler.IRequestHandler;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class ConfigVerifyHandler implements IRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfigVerifyHandler.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ConfigFileManager configFileManager;

    public ConfigVerifyHandler(ConfigFileManager configFileManager) {
        this.configFileManager = configFileManager;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            File configDir = configFileManager.getConfigDir();
            if (configDir == null || !configDir.exists()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("tasks", Collections.emptyList());
                sendResponse(ctx, request, HttpResponseStatus.OK, ApiResponse.success(data));
                return;
            }

            File[] files = configDir.listFiles((dir, name) ->
                    name.startsWith("task_") && name.endsWith(".json"));

            List<Map<String, Object>> taskList = new ArrayList<>();
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));
                for (File file : files) {
                    Map<String, Object> taskInfo = new LinkedHashMap<>();
                    byte[] content = Files.readAllBytes(file.toPath());
                    String contentStr = new String(content, StandardCharsets.UTF_8);

                    // 尝试反序列化获取 taskId 和 taskName
                    try {
                        AgentTaskConfig config = GSON.fromJson(contentStr, AgentTaskConfig.class);
                        taskInfo.put("taskId", config.getTaskId());
                        taskInfo.put("taskName", config.getTaskName());
                    } catch (Exception e) {
                        taskInfo.put("taskId", null);
                        taskInfo.put("taskName", null);
                    }
                    taskInfo.put("content", contentStr);
                    taskList.add(taskInfo);
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tasks", taskList);
            sendResponse(ctx, request, HttpResponseStatus.OK, ApiResponse.success(data));
        } catch (Exception e) {
            log.error("配置校验失败", e);
            sendResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    ApiResponse.failure(500, "配置校验失败: " + e.getMessage()));
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest request,
                              HttpResponseStatus status, Object data) {
        String content = GSON.toJson(data);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer(content, StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response);
        } else {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
