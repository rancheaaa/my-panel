package com.cq.agent.handler.chunk;

import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.ChunkDownloadResponse;
import com.cq.agent.dto.ChunkedDownloadResult;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Base64;

public class ChunkDownloadHandler extends BaseHandler {

    public ChunkDownloadHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }
        String startParam = getQueryParam(request, "start", null);
        String endParam = getQueryParam(request, "end", null);
        long start = 0;
        long end = -1;
        try {
            if (startParam != null && !startParam.isEmpty()) {
                start = Long.parseLong(startParam);
            }
            if (endParam != null && !endParam.isEmpty()) {
                end = Long.parseLong(endParam);
            }
        } catch (NumberFormatException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid 'start' or 'end' parameter"));
            return;
        }

        ApiResponse<ChunkedDownloadResult> result = chunkedTransferService.downloadRange(path, start, end);
        if (!result.isSuccess()) {
            sendResponse(ctx, HttpResponseStatus.OK, createErrorResponse(result.getMsg()));
            return;
        }
        ChunkedDownloadResult dataResult = result.getData();

        ChunkDownloadResponse resp = new ChunkDownloadResponse();
        resp.setSuccess(true);
        resp.setData(Base64.getEncoder().encodeToString(dataResult.getData()));
        resp.setEncoding("base64");
        resp.setRangeStart(dataResult.getRangeStart());
        resp.setRangeEnd(dataResult.getRangeEnd());
        resp.setTotalSize(dataResult.getTotalSize());
        resp.setFileName(dataResult.getFileName());
        sendResponse(ctx, HttpResponseStatus.OK, gson.toJson(resp));
    }
}
