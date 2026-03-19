package com.cq.agent.handler.chunk;

import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.ChunkCancelRequest;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.JsonSyntaxException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

public class ChunkCancelHandler extends BaseHandler {

    public ChunkCancelHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            ChunkCancelRequest body = parseBody(request, ChunkCancelRequest.class);
            if (body == null || body.getTransferId() == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'transferId' field is required"));
                return;
            }
            // 从HTTP头获取traceid
            String traceId = request.headers().get("X-Trace-Id");
            ApiResponse<Void> result = chunkedTransferService.cancelUpload(body.getTransferId(), traceId);
            sendServiceResult(ctx,request, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }
}
