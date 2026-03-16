package com.cq.agent.handler.file;

import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.MfmtRequest;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.model.FileInfo;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.JsonSyntaxException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

public class MfmtHandler extends BaseHandler {

    public MfmtHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            MfmtRequest body = parseBody(request, MfmtRequest.class);
            if (body == null || body.getPath() == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' field is required"));
                return;
            }
            ApiResponse<FileInfo> result = fileService.setModificationTime(body.getPath(), body.getTimestamp());
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }
}
