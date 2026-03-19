package com.cq.agent.handler.chunk;

import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.ChunkInitRequest;
import com.cq.agent.dto.ChunkStatusData;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.model.UploadSession;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.JsonSyntaxException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkInitHandler extends BaseHandler {

    private static final Logger log = LoggerFactory.getLogger(ChunkInitHandler.class);

    public ChunkInitHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            // 从HTTP头获取traceid
            String traceId = request.headers().get("X-Trace-Id");

            ChunkInitRequest body = parseBody(request, ChunkInitRequest.class);
            if (body == null || body.getDestFileDir() == null || body.getDestFileName() == null || body.getTotalSize() <= 0) {
                sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "'destFileDir', 'destFileName' and 'totalSize' (positive) are required"));
                return;
            }
            log.debug("[traceId={}] receive init upload request: {}", traceId, body);
            ApiResponse<ChunkStatusData> result = chunkedTransferService.initUpload(
                    traceId, body.getTransferId(), body.getDestFileDir(), body.getDestFileName(), body.getTotalSize());
            sendServiceResult(ctx, request, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid JSON format"));
        }
    }
}