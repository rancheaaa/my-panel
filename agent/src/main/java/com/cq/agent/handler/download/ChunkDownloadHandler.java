package com.cq.agent.handler.download;

import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ChunkDownloadRequest;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkDownloadHandler extends BaseHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChunkDownloadHandler.class);
    private static final int DEFAULT_CHUNK_SIZE = 4 * 1024 * 1024;

    public ChunkDownloadHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String traceId = request.headers().get("X-Trace-Id");
        if (traceId == null || traceId.isEmpty()) {
            traceId = "N/A";
        }
        String uri = request.uri();
        logger.debug("[traceId={}] ChunkDownloadHandler handling request: {}", traceId, uri);

        try {
            ChunkDownloadRequest downloadRequest = parseBody(request, ChunkDownloadRequest.class);
            if (downloadRequest == null) {
                sendErrorResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, ApiCode.INVALID_PARAMETER, "Request body is required");
                return;
            }

            if (downloadRequest.getTransferId() == null || downloadRequest.getTransferId().isBlank()) {
                sendErrorResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, ApiCode.INVALID_PARAMETER, "transferId is required");
                return;
            }

            if (downloadRequest.getChunkIndex() < 0) {
                sendErrorResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, ApiCode.INVALID_PARAMETER, "chunkIndex is required and must be >= 0");
                return;
            }

            if (downloadRequest.getDestFileDir() == null || downloadRequest.getDestFileDir().isBlank()) {
                sendErrorResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, ApiCode.INVALID_PARAMETER, "destFileDir is required");
                return;
            }

            if (downloadRequest.getDestFileName() == null || downloadRequest.getDestFileName().isBlank()) {
                sendErrorResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, ApiCode.INVALID_PARAMETER, "destFileName is required");
                return;
            }

            String requestTraceId = downloadRequest.getTraceId();
            if (requestTraceId != null && !requestTraceId.isEmpty()) {
                traceId = requestTraceId;
            }

            handleChunkDownload(ctx, request, downloadRequest.getTransferId(), downloadRequest.getChunkIndex(), 
                    downloadRequest.getDestFileDir(), downloadRequest.getDestFileName(), traceId);
        } catch (Exception e) {
            logger.error("[traceId={}] ChunkDownloadHandler error", traceId, e);
            sendErrorResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR, ApiCode.INTERNAL_SERVER_ERROR, "Failed to download chunk: " + e.getMessage());
        }
    }

    private void handleChunkDownload(ChannelHandlerContext ctx, FullHttpRequest request, String transferId, int chunkIndex, String destFileDir, String destFileName, String traceId) throws IOException {
        logger.debug("[traceId={}] Downloading chunk {} for transferId: {} using zero-copy", traceId, chunkIndex, transferId);

        String destFilePath = destFileDir + "/" + destFileName;
        Path filePath = Paths.get(destFilePath);
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File not found: " + destFilePath);
        }

        java.io.File file = filePath.toFile();
        long fileSize = file.length();
        int chunkSize = DEFAULT_CHUNK_SIZE;
        long position = (long) chunkIndex * chunkSize;

        if (position >= fileSize) {
            throw new IllegalArgumentException("Chunk index out of range: " + chunkIndex);
        }

        long bytesToRead = Math.min(chunkSize, fileSize - position);

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytesToRead);
        response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + destFileName + "_chunk_" + chunkIndex + "\"");
        response.headers().set(HttpHeaderNames.ACCEPT_RANGES, "bytes");
        response.headers().set("X-Transfer-Id", transferId);
        response.headers().set("X-Chunk-Index", String.valueOf(chunkIndex));
        response.headers().set("X-File-Size", String.valueOf(fileSize));
        response.headers().set("X-Range-Start", String.valueOf(position));
        response.headers().set("X-Range-End", String.valueOf(position + bytesToRead - 1));

        ctx.write(response);

        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            raf.seek(position);
            DefaultFileRegion region = new DefaultFileRegion(raf.getChannel(), position, bytesToRead);
            ctx.write(region);
            
            ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

            RandomAccessFile finalRaf = raf;
            lastContentFuture.addListener((ChannelFutureListener) future -> {
                try {
                    if (future.isSuccess()) {
                        logger.info("[traceId={}] Chunk downloaded successfully using zero-copy: transferId={}, chunkIndex={}, totalChunks={}, range={}-{}, size={}",
                                traceId, transferId, chunkIndex + 1, (int) fileSize / chunkSize, (int) position, position + bytesToRead - 1, bytesToRead);
                    } else {
                        logger.error("[traceId={}] Chunk downloaded failed using zero-copy: transferId={}, chunkIndex={}, totalChunks={}, range={}-{}, size={}",
                                traceId, transferId, chunkIndex + 1, (int) fileSize / chunkSize, (int) position, position + bytesToRead - 1, bytesToRead, future.cause());
                    }
                } finally {
                    try {
                        if (region.refCnt() > 0) {
                            region.release();
                        }
                    } catch (Exception e) {
                        logger.warn("[traceId={}] Failed to release file region", traceId, e);
                    }
                    try {
                        finalRaf.close();
                    } catch (IOException ignored) {
                    }
                    if (!HttpUtil.isKeepAlive(request)) {
                        ctx.close();
                    }
                }
            });
        } catch (Exception e) {
            logger.error("[traceId={}] ChunkDownloadHandler error", traceId, e);
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e2) {
                    logger.error("[traceId={}] Failed to close file", traceId, e2);
                }
            }
            throw e;
        }
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, ApiCode code, String message) {
        String errorJson = createErrorResponse(code, message);
        sendResponse(ctx, request, status, errorJson);
    }
}