package com.cq.agent.handler.file;

import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.RenameRequest;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.model.FileInfo;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.JsonSyntaxException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

public class RenameHandler extends BaseHandler {

    public RenameHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            RenameRequest body = parseBody(request, RenameRequest.class);
            if (body == null || body.getFrom() == null || body.getTo() == null) {
                sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'from' and 'to' fields are required"));
                return;
            }
            ApiResponse<FileInfo> result = fileService.rename(body.getFrom(), body.getTo());
            sendServiceResult(ctx,request, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }
}
