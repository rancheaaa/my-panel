package com.cq.agent.handler.download;

import com.cq.agent.client.Util;
import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ChunkDownloadInfoRequest;
import com.cq.agent.dto.ChunkDownloadInfoResponse;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class ChunkDownloadInfoHandler extends BaseHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChunkDownloadInfoHandler.class);
    private static final int DEFAULT_CHUNK_SIZE = 4 * 1024 * 1024;

    public ChunkDownloadInfoHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String traceId = request.headers().get("X-Trace-Id");
        if (traceId == null || traceId.isEmpty()) {
            traceId = "N/A";
        }
        String uri = request.uri();
        logger.debug("[traceId={}] ChunkDownloadInfoHandler handling request: {}", traceId, uri);

        try {
            String body = request.content().toString(io.netty.util.CharsetUtil.UTF_8);
            ChunkDownloadInfoRequest req = gson.fromJson(body, ChunkDownloadInfoRequest.class);

            if (req.getRemoteFilePath() == null || req.getRemoteFilePath().isBlank()) {
                sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_PARAMETER, "remoteFilePath is required"));
                return;
            }

            Path filePath = Paths.get(req.getRemoteFilePath());
            if (!Files.exists(filePath)) {
                sendResponse(ctx, request, HttpResponseStatus.NOT_FOUND, createErrorResponse(ApiCode.FILE_NOT_FOUND, "Remote file not found: " + req.getRemoteFilePath()));
                return;
            }

            File file = filePath.toFile();
            long fileSize = file.length();
            if (fileSize <= 0) {
                sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_FILE_SIZE, "Invalid file size: " + fileSize));
                return;
            }

            String transferId = UUID.randomUUID().toString().replace("-", "");
            int chunkSize = DEFAULT_CHUNK_SIZE;
            int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

            ChunkDownloadInfoResponse response = new ChunkDownloadInfoResponse();
            response.setTransferId(transferId);
            response.setFileSize(fileSize);
            response.setTotalChunks(totalChunks);
            response.setChunkSize(chunkSize);
            response.setFileName(file.getName());
            response.setInitTime(Util.currentTime());

            logger.info("[traceId={}] Download info created: transferId={}, file={}, size={}, totalChunks={}",
                    traceId, transferId, req.getRemoteFilePath(), fileSize, totalChunks);

            sendSuccessResponse(ctx, request, response);
        } catch (Exception e) {
            logger.error("[traceId={}] ChunkDownloadInfoHandler error", traceId, e);
            sendResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR, createErrorResponse(ApiCode.INTERNAL_SERVER_ERROR, "Failed to get download info: " + e.getMessage()));
        }
    }
}