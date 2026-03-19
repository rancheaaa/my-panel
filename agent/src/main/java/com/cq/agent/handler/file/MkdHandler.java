package com.cq.agent.handler.file;

import com.cq.agent.dto.ApiResponse;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.model.FileInfo;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

public class MkdHandler extends BaseHandler {

    public MkdHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }
        ApiResponse<FileInfo> result = fileService.makeDirectory(path);
        sendServiceResult(ctx,request, result);
    }
}
