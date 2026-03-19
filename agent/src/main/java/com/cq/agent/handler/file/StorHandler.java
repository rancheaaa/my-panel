package com.cq.agent.handler.file;

import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.StorRequest;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.model.FileInfo;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.JsonSyntaxException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class StorHandler extends BaseHandler {

    public StorHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            StorRequest body = parseBody(request, StorRequest.class);
            if (body == null || body.getPath() == null) {
                sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "'path' field is required"));
                return;
            }
            if (body.getContent() == null) {
                sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "'content' field is required"));
                return;
            }
            byte[] content = "base64".equals(body.getEncoding())
                    ? Base64.getDecoder().decode(body.getContent())
                    : body.getContent().getBytes(StandardCharsets.UTF_8);
            ApiResponse<FileInfo> result = fileService.store(body.getPath(), content);
            sendServiceResult(ctx,request, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid JSON format"));
        }
    }
}