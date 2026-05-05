package com.cq.agent.handler.batch;

import com.cq.agent.batch.scheduler.BatchScanScheduler;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

import java.util.List;
import java.util.Map;

public class BatchScanScheduleHandler extends BaseHandler {
    private static final Gson gson = new Gson();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BatchScanScheduleHandler.class);
    private final BatchScanScheduler scanScheduler;

    public BatchScanScheduleHandler(FileService fileService, ChunkedTransferService chunkedTransferService,
            BatchScanScheduler scanScheduler) {
        super(fileService, chunkedTransferService);
        this.scanScheduler = scanScheduler;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            Map<String, Object> body = parseBody(request, Map.class);
            if (body == null) {
                sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST,
                        createErrorResponse(com.cq.agent.dto.ApiCode.INVALID_REQUEST, "Invalid request body"));
                return;
            }

            String action = (String) body.get("action");
            if (action == null) {
                sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST,
                        createErrorResponse(com.cq.agent.dto.ApiCode.INVALID_REQUEST, "action is required"));
                return;
            }

            switch (action.toLowerCase()) {
                case "upsert":
                    handleUpsert(ctx, request, body);
                    break;
                case "remove":
                    handleRemove(ctx, request, body);
                    break;
                default:
                    sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST,
                            createErrorResponse(com.cq.agent.dto.ApiCode.INVALID_REQUEST, "Unknown action: " + action));
            }
        } catch (Exception e) {
            log.error("BatchScanScheduleHandler error: {}", e.getMessage(), e);
            sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    createErrorResponse(com.cq.agent.dto.ApiCode.GENERIC_ERROR, "Error: " + e.getMessage()));
        }
    }

    private void handleUpsert(ChannelHandlerContext ctx, FullHttpRequest request, Map<String, Object> body) {
        Number taskIdNum = (Number) body.get("taskId");
        if (taskIdNum == null) {
            sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST,
                    createErrorResponse(com.cq.agent.dto.ApiCode.INVALID_REQUEST, "taskId is required"));
            return;
        }
        Long taskId = taskIdNum.longValue();

        String cronExpression = (String) body.get("cronExpression");
        if (cronExpression == null || cronExpression.isBlank()) {
            sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST,
                    createErrorResponse(com.cq.agent.dto.ApiCode.INVALID_REQUEST, "cronExpression is required"));
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> scanConfig = (Map<String, Object>) body.get("scanConfig");
        String proxyBaseUrl = (String) body.get("proxyBaseUrl");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> targetAgents = gson.fromJson(gson.toJson(body.get("targetAgents")),
                new TypeToken<List<Map<String, Object>>>() {
                }.getType());
        @SuppressWarnings("unchecked")
        Map<String, String> targetDirs = gson.fromJson(gson.toJson(body.get("targetDirs")),
                new TypeToken<Map<String, String>>() {
                }.getType());
        Number preserveDirStructure = (Number) body.get("preserveDirStructure");
        Number maxBandwidthBytesPerSec = (Number) body.get("maxBandwidthBytesPerSec");

        scanScheduler.upsertTask(taskId, cronExpression, scanConfig, proxyBaseUrl, targetAgents, targetDirs,
                preserveDirStructure != null ? preserveDirStructure.intValue() : 1,
                maxBandwidthBytesPerSec != null ? maxBandwidthBytesPerSec.longValue() : null);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "Scheduled task " + taskId + " with cron: " + cronExpression);
        sendSuccessResponse(ctx, request, result);
    }

    private void handleRemove(ChannelHandlerContext ctx, FullHttpRequest request, Map<String, Object> body) {
        Number taskIdNum = (Number) body.get("taskId");
        if (taskIdNum == null) {
            sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST,
                    createErrorResponse(com.cq.agent.dto.ApiCode.INVALID_REQUEST, "taskId is required"));
            return;
        }
        scanScheduler.removeTask(taskIdNum.longValue());

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "Removed scheduled task " + taskIdNum);
        sendSuccessResponse(ctx, request, result);
    }
}
