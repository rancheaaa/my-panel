package com.cq.agent.handler.batch;

import com.cq.agent.client.upload.BatchTaskSchedulerUploader;
import com.cq.agent.handler.IRequestHandler;
import com.cq.agent.dto.ApiResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 批量任务控制Handler
 * 处理Proxy发送的任务控制指令：
 * - PAUSE: 暂停任务
 * - RESUME: 恢复任务
 * - DELETE: 删除任务
 */
public class BatchTaskControlHandler implements IRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(BatchTaskControlHandler.class);
    private static final Gson GSON = new Gson();

    private final BatchTaskSchedulerUploader batchTaskUploader;

    public BatchTaskControlHandler(BatchTaskSchedulerUploader batchTaskUploader) {
        this.batchTaskUploader = batchTaskUploader;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (request.method() != HttpMethod.POST) {
            sendResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, ApiResponse.failure(405, "只允许POST请求"));
            return;
        }

        try {
            String body = request.content().toString(StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            if (!json.has("taskId") || json.get("taskId") == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, ApiResponse.failure(400, "缺少必填字段: taskId"));
                return;
            }

            if (!json.has("action") || json.get("action") == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, ApiResponse.failure(400, "缺少必填字段: action"));
                return;
            }

            Long taskId = json.get("taskId").getAsLong();
            String action = json.get("action").getAsString();

            log.info("📩 收到任务控制指令: taskId={}, action={}", taskId, action);

            boolean success;
            String message;

            switch (action.toUpperCase()) {
                case "PAUSE":
                    batchTaskUploader.pauseTask(taskId);
                    success = true;
                    message = "任务已暂停";
                    break;
                case "RESUME":
                    batchTaskUploader.resumeTask(taskId);
                    success = true;
                    message = "任务已恢复";
                    break;
                case "DELETE":
                    batchTaskUploader.deleteTask(taskId);
                    success = true;
                    message = "任务已删除";
                    break;
                case "STATUS":
                    boolean isRunning = batchTaskUploader.isTaskRunning(taskId);
                    success = true;
                    message = isRunning ? "任务运行中" : "任务未运行";
                    break;
                default:
                    success = false;
                    message = "未知操作: " + action;
                    log.warn("⚠️  未知任务控制指令: action={}", action);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("taskId", taskId);
            result.put("action", action);
            result.put("message", message);

            if (success) {
                sendResponse(ctx, HttpResponseStatus.OK, ApiResponse.success(result));
            } else {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, ApiResponse.failure(400, message));
            }

        } catch (Exception e) {
            log.error("❌ 处理任务控制指令失败: error={}", e.getMessage());
            sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, ApiResponse.failure(500, "处理失败: " + e.getMessage()));
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, Object data) {
        String content = GSON.toJson(data);
        io.netty.buffer.ByteBuf buffer = Unpooled.copiedBuffer(content, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buffer.readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
