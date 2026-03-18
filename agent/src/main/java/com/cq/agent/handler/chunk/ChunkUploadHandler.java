package com.cq.agent.handler.chunk;

import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.ChunkUploadRequest;
import com.cq.agent.dto.ChunkUploadResultData;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.JsonSyntaxException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class ChunkUploadHandler extends BaseHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChunkUploadHandler.class);

    public ChunkUploadHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            ChunkUploadRequest body = parseBody(request, ChunkUploadRequest.class);
            if (body == null || body.getTransferId() == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'transferId' field is required"));
                return;
            }

            // Log received metadata
            logger.debug("Received chunk upload request: transferId={}, chunkIndex={}, sourceAgentId={}, sourceFileDir={}, sourceFileName={}, destFileDir={}, destFileName={}",
                    body.getTransferId(), body.getChunkIndex(), body.getSourceAgentId(), body.getSourceFileDir(), body.getSourceFileName(), body.getDestFileDir(), body.getDestFileName());

            if (body.getContent() == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'content' field is required"));
                return;
            }
            byte[] content = "base64".equals(body.getEncoding())
                    ? Base64.getDecoder().decode(body.getContent())
                    : body.getContent().getBytes(StandardCharsets.UTF_8);
            ApiResponse<ChunkUploadResultData> result = chunkedTransferService.uploadChunk(body, content);
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }
}
