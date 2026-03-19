package com.cq.agent.handler.file;

import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.model.FileInfo;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

public class RetrRawHandler extends BaseHandler {

    public RetrRawHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String pathParam = getQueryParam(request, "path", null);
        if (pathParam == null) {
            sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "'path' parameter is required"));
            return;
        }

        ApiResponse<FileInfo> statResult = fileService.stat(pathParam);
        if (!statResult.isSuccess()) {
            sendResponse(ctx,request, HttpResponseStatus.OK, createErrorResponse(ApiCode.GET_STATUS_FAILED, statResult.getMsg()));
            return;
        }

        FileInfo info = statResult.getData();
        long fileSize = info.getSize();

        String startParam = getQueryParam(request, "start", null);
        String lengthParam = getQueryParam(request, "length", null);

        long start = 0;
        if (startParam != null && !startParam.isEmpty()) {
            try {
                start = Long.parseLong(startParam);
            } catch (NumberFormatException e) {
                sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid 'start' parameter"));
                return;
            }
        }

        if (start < 0 || start >= fileSize) {
            sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_RANGE, "Invalid 'start' parameter range"));
            return;
        }

        long length;
        if (lengthParam != null && !lengthParam.isEmpty()) {
            try {
                length = Long.parseLong(lengthParam);
            } catch (NumberFormatException e) {
                sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid 'length' parameter"));
                return;
            }
        } else {
            length = fileSize - start;
        }

        if (length <= 0 || start + length > fileSize) {
            sendResponse(ctx,request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_RANGE, "Invalid range"));
            return;
        }

        Path path = Path.of(info.getPath());

        try {
            RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
            raf.seek(start);
            long contentLength = length;

            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, start == 0 && length == fileSize
                    ? HttpResponseStatus.OK
                    : HttpResponseStatus.PARTIAL_CONTENT);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, contentLength);
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + info.getName() + "\"");
            if (start != 0 || length != fileSize) {
                String contentRange = "bytes " + start + "-" + (start + length - 1) + "/" + fileSize;
                response.headers().set(HttpHeaderNames.CONTENT_RANGE, contentRange);
            }
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

            ctx.write(response);

            DefaultFileRegion region = new DefaultFileRegion(raf.getChannel(), start, contentLength);
            ChannelFuture sendFileFuture = ctx.write(region);
            ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

            lastContentFuture.addListener((ChannelFutureListener) future -> {
                try {
                    region.release();
                } finally {
                    try {
                        raf.close();
                    } catch (IOException ignored) {
                    }
                    ctx.close();
                }
            });
        } catch (IOException e) {
            sendResponse(ctx,request, HttpResponseStatus.INTERNAL_SERVER_ERROR, createErrorResponse(ApiCode.READ_FILE_FAILED, "Failed to read file: " + e.getMessage()));
        }
    }
}