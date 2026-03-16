package com.cq.agent.handler.file;

import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.StouRequest;
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

public class StouHandler extends BaseHandler {

    public StouHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            StouRequest body = parseBody(request, StouRequest.class);
            if (body == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Request body is required"));
                return;
            }
            String directory = body.getDirectory() != null ? body.getDirectory() : ".";
            if (body.getContent() == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'content' field is required"));
                return;
            }
            byte[] content = "base64".equals(body.getEncoding())
                    ? Base64.getDecoder().decode(body.getContent())
                    : body.getContent().getBytes(StandardCharsets.UTF_8);
            ApiResponse<FileInfo> result = fileService.storeUnique(directory, content, body.getPrefix());
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }
}
