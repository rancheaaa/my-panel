package com.cq.agent.handler.file;

import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.RetrResponse;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Base64;

public class RetrHandler extends BaseHandler {

    public RetrHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }

        String mode = getQueryParam(request, "mode", "binary");
        String charset = getQueryParam(request, "charset", "UTF-8");

        if ("text".equals(mode)) {
            ApiResponse<String> result = fileService.retrieveText(path, charset);
            sendServiceResult(ctx,request, result);
        } else {
            ApiResponse<byte[]> result = fileService.retrieve(path);
            if (result.isSuccess()) {
                RetrResponse resp = new RetrResponse();
                resp.setSuccess(true);
                resp.setData(Base64.getEncoder().encodeToString(result.getData()));
                resp.setSize(result.getData().length);
                resp.setEncoding("base64");
                sendResponse(ctx, HttpResponseStatus.OK, gson.toJson(resp));
            } else {
                sendResponse(ctx, HttpResponseStatus.OK, createErrorResponse(result.getMsg()));
            }
        }
    }
}
