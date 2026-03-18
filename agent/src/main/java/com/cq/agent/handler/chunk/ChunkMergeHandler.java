package com.cq.agent.handler.chunk;

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
            ChunkMergeRequest body = parseBody(request, ChunkMergeRequest.class);
            if (body == null || body.getTransferId() == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'transferId' field is required"));
                return;
            }
            logger.debug("Received merge request for transferId: {}", body.getTransferId());
            ApiResponse<MergeResultData> result = chunkedTransferService.mergeChunks(body.getTransferId());
            if (result.isSuccess()) {
                logger.debug("Merge successful for transferId: {}", body.getTransferId());
            } else {
                logger.debug("Merge failed for transferId: {} - {}", body.getTransferId(), result.getMsg());
            }
            sendServiceResult(ctx, request, result);
        } catch (JsonSyntaxException e) {
            logger.debug("Invalid JSON format for merge request", e);
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }
}