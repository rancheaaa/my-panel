package com.cq.agent.handler.file;

import com.cq.agent.dto.ApiResponse;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

public class ChecksumHandler extends BaseHandler {

    public ChecksumHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }
        String algorithm = getQueryParam(request, "algorithm", "MD5");
        ApiResponse<String> result = fileService.checksum(path, algorithm);
        sendServiceResult(ctx,request, result);
    }
}
