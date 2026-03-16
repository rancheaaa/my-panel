package com.cq.agent.handler.chunk;

import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.ChunkInitRequest;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.model.UploadSession;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.JsonSyntaxException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

public class ChunkInitHandler extends BaseHandler {

    public ChunkInitHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            ChunkInitRequest body = parseBody(request, ChunkInitRequest.class);
            if (body == null || body.getTargetPath() == null || body.getFileName() == null || body.getTotalSize() <= 0) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'targetPath', 'fileName' and 'totalSize' (positive) are required"));
                return;
            }
            ApiResponse<UploadSession> result = chunkedTransferService.initUpload(
                    body.getTransferId(), body.getTargetPath(), body.getFileName(), body.getTotalSize());
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }
}
