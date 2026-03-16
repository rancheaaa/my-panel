package com.cq.agent.handler.chunk;

import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.ChunkMergeRequest;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.model.FileInfo;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.JsonSyntaxException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

public class ChunkMergeHandler extends BaseHandler {

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
            ApiResponse<FileInfo> result = chunkedTransferService.mergeChunks(body.getTransferId());
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }
}
