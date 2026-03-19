package com.cq.agent.handler.file;

import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

public class SizeHandler extends BaseHandler {

    public SizeHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "'path' parameter is required"));
            return;
        }
        ApiResponse<Long> result = fileService.getSize(path);
        sendServiceResult(ctx,request, result);
    }
}