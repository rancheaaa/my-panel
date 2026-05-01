package com.cq.agent.handler.batch;

import com.cq.agent.batch.queue.BatchTransferQueueManager;
import com.cq.agent.batch.queue.BatchUploadTask;
import com.cq.agent.batch.queue.QueueMetrics;
import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BatchDispatchHandler extends BaseHandler
{
    private static final Gson gson = new Gson();
    private final BatchTransferQueueManager queueManager;

    public BatchDispatchHandler(FileService fileService, ChunkedTransferService chunkedTransferService, BatchTransferQueueManager queueManager)
    {
        super(fileService, chunkedTransferService);
        this.queueManager = queueManager;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request)
    {
        String body = request.content().toString(java.nio.charset.StandardCharsets.UTF_8);
        if (body == null || body.isEmpty())
        {
            sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST,
                    createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid request body"));
            return;
        }
        Map<String, Object> dispatchRequest = gson.fromJson(body, new TypeToken<Map<String, Object>>(){}.getType());
        if (dispatchRequest == null)
        {
            sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST,
                    createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid request body"));
            return;
        }
        List<Map<String, Object>> subtasks = (List<Map<String, Object>>) dispatchRequest.get("subtasks");
        Map<String, String> agentTargetDirs = (Map<String, String>) dispatchRequest.get("agentTargetDirs");
        Boolean preserveDirStructure = (Boolean) dispatchRequest.get("preserveDirStructure");
        List<Map<String, Object>> rejections = new ArrayList<>();
        int receivedCount = 0;
        int rejectedCount = 0;
        if (subtasks != null)
        {
            for (Map<String, Object> item : subtasks)
            {
                BatchUploadTask task = new BatchUploadTask();
                if (item.get("subtaskId") != null) task.setSubtaskId(((Number) item.get("subtaskId")).longValue());
                if (item.get("taskId") != null) task.setTaskId(((Number) item.get("taskId")).longValue());
                task.setFilePath((String) item.get("filePath"));
                if (item.get("fileSizeBytes") != null) task.setFileSizeBytes(((Number) item.get("fileSizeBytes")).longValue());
                task.setTargetAgentId((String) item.get("targetAgentId"));
                task.setTargetAgentApiUrl((String) item.get("targetAgentApiUrl"));
                String agentTargetDir = item.get("targetDir") != null ? (String) item.get("targetDir") :
                        (agentTargetDirs != null ? agentTargetDirs.get(task.getTargetAgentId()) : null);
                task.setTargetDir(agentTargetDir);
                task.setPreserveDirStructure(preserveDirStructure != null && preserveDirStructure);
                if (item.get("priority") != null) task.setPriority(((Number) item.get("priority")).intValue());
                if (queueManager.enqueue(task))
                {
                    receivedCount++;
                }
                else
                {
                    rejectedCount++;
                    rejections.add(Map.of(
                            "subtaskId", task.getSubtaskId(),
                            "reason", "SEND_QUEUE_FULL"
                    ));
                }
            }
        }
        long estimatedQueueDrainTimeSec = 0L;
        QueueMetrics metrics = queueManager.getMetrics();
        if (metrics.getProcessingRatePerSec() > 0)
        {
            estimatedQueueDrainTimeSec = BigDecimal.valueOf(metrics.getSendQueueDepth())
                    .divide(BigDecimal.valueOf(metrics.getProcessingRatePerSec()), 0, RoundingMode.UP)
                    .longValue();
        }
        Object dispatchId = dispatchRequest.get("dispatchId");
        Object bandwidthLimit = dispatchRequest.get("maxBandwidthBytesPerSec");
        sendSuccessResponse(ctx, request, Map.of(
                "dispatchId", dispatchId,
                "receivedCount", receivedCount,
                "rejectedCount", rejectedCount,
                "rejections", rejections,
                "estimatedQueueDrainTimeSec", estimatedQueueDrainTimeSec,
                "appliedBandwidthLimit", bandwidthLimit
        ));
    }
}
