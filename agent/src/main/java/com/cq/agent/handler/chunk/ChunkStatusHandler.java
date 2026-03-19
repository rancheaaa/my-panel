package com.cq.agent.handler.chunk;

import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.ChunkStatusData;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Map;

public class ChunkStatusHandler extends BaseHandler {

    public ChunkStatusHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String transferId = getQueryParam(request, "transferId", null);
        if (transferId == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "'transferId' parameter is required"));
            return;
        }
        String traceid = request.headers().get("X-Trace-Id");
        ApiResponse<ChunkStatusData> result = chunkedTransferService.getUploadStatus(transferId, traceid);
        sendServiceResult(ctx,request, result);
    }
}
