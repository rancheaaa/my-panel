package com.cq.agent.handler.file;

import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

public class RmdHandler extends BaseHandler {

    public RmdHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "'path' parameter is required"));
            return;
        }
        boolean recursive = "true".equals(getQueryParam(request, "recursive", "false"));
        ApiResponse<Void> result = fileService.removeDirectory(path, recursive);
        sendServiceResult(ctx,request, result);
    }
}