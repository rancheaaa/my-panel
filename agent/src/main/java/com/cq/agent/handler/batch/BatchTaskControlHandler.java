package com.cq.agent.handler.batch;

import com.cq.agent.batch.queue.BatchTransferQueueManager;
import com.cq.agent.batch.scheduler.BatchScanScheduler;
import com.cq.agent.dto.ApiCode;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.Gson;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class BatchTaskControlHandler extends BaseHandler {
    private static final Logger log = LoggerFactory.getLogger(BatchTaskControlHandler.class);
    private static final Gson gson = new Gson();
    private final BatchTransferQueueManager queueManager;
    private final BatchScanScheduler scanScheduler;

    public BatchTaskControlHandler(FileService fileService, ChunkedTransferService chunkedTransferService,
            BatchTransferQueueManager queueManager, BatchScanScheduler scanScheduler) {
        super(fileService, chunkedTransferService);
        this.queueManager = queueManager;
        this.scanScheduler = scanScheduler;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            Map<String, Object> body = parseBody(request, Map.class);
            if (body == null) {
                sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST,
                        createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid request body"));
                return;
            }

            String action = (String) body.get("action");
            Number taskIdNum = (Number) body.get("taskId");
            if (action == null || taskIdNum == null) {
                sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST,
                        createErrorResponse(ApiCode.INVALID_REQUEST, "action and taskId are required"));
                return;
            }
            Long taskId = taskIdNum.longValue();

            switch (action.toLowerCase()) {
                case "pause" -> {
                    queueManager.pauseTask(taskId);
                    if (scanScheduler != null) {
                        scanScheduler.pauseTask(taskId);
                    }
                    log.info("Task {} paused", taskId);
                    sendSuccessResponse(ctx, request, Map.of("success", true, "message", "Task " + taskId + " paused"));
                }
                case "resume" -> {
                    queueManager.resumeTask(taskId);
                    if (scanScheduler != null) {
                        scanScheduler.resumeTask(taskId);
                    }
                    log.info("Task {} resumed", taskId);
                    sendSuccessResponse(ctx, request, Map.of("success", true, "message", "Task " + taskId + " resumed"));
                }
                case "cancel" -> {
                    queueManager.cancelTask(taskId);
                    if (scanScheduler != null) {
                        scanScheduler.removeTask(taskId);
                    }
                    log.info("Task {} cancelled", taskId);
                    sendSuccessResponse(ctx, request, Map.of("success", true, "message", "Task " + taskId + " cancelled"));
                }
                default -> sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST,
                        createErrorResponse(ApiCode.INVALID_REQUEST, "Unknown action: " + action));
            }
        } catch (Exception e) {
            log.error("BatchTaskControlHandler error: {}", e.getMessage(), e);
            sendResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    createErrorResponse(ApiCode.GENERIC_ERROR, "Error: " + e.getMessage()));
        }
    }
}
