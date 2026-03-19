package com.cq.agent.handler.chunk;

import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.ChunkMergeRequest;
import com.cq.agent.dto.MergeResultData;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.JsonSyntaxException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkMergeHandler extends BaseHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChunkMergeHandler.class);

    public ChunkMergeHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            // 从HTTP头获取traceid
            String traceId = request.headers().get("X-Trace-Id");
            if (traceId == null || traceId.isEmpty()) {
                traceId = java.util.UUID.randomUUID().toString();
            }

            ChunkMergeRequest body = parseBody(request, ChunkMergeRequest.class);
            if (body == null || body.getTransferId() == null) {
                sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "'transferId' field is required"));
                return;
            }
            logger.debug("[traceId={}] Received merge request: transferId={}", traceId, body.getTransferId());
            ApiResponse<MergeResultData> result = chunkedTransferService.mergeChunks(traceId, body.getTransferId());
            if (result.isSuccess()) {
                logger.debug("[traceId={}] Merge successful for transferId: {}", traceId, body.getTransferId());
            } else {
                logger.debug("[traceId={}] Merge failed for transferId: {} - {}", traceId, body.getTransferId(), result.getMsg());
            }
            sendServiceResult(ctx, request, result);
        } catch (JsonSyntaxException e) {
            logger.debug("Invalid JSON format for merge request", e);
            sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid JSON format"));
        }
    }
}