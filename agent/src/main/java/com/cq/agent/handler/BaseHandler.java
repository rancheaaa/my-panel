package com.cq.agent.handler;

import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class BaseHandler implements IRequestHandler {

    protected static final Gson gson = new Gson();
    protected final FileService fileService;
    protected final ChunkedTransferService chunkedTransferService;

    public BaseHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        this.fileService = fileService;
        this.chunkedTransferService = chunkedTransferService;
    }

    protected String getQueryParam(FullHttpRequest request, String name, String defaultValue) {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        List<String> values = decoder.parameters().get(name);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return defaultValue;
    }

    protected <T> T parseBody(FullHttpRequest request, Class<T> type) {
        String body = request.content().toString(StandardCharsets.UTF_8);
        if (body == null || body.isEmpty()) {
            return null;
        }
        return gson.fromJson(body, type);
    }

    protected <T> void sendServiceResult(ChannelHandlerContext ctx, ApiResponse<T> result) {
        if (result.isSuccess()) {
            sendSuccessResponse(ctx, result.getData());
        } else {
            sendResponse(ctx, HttpResponseStatus.OK, gson.toJson(result));
        }
    }

    protected void sendSuccessResponse(ChannelHandlerContext ctx, Object data) {
        sendResponse(ctx, HttpResponseStatus.OK, gson.toJson(ApiResponse.success(data)));
    }

    protected String createErrorResponse(ApiCode code, String message) {
        return gson.toJson(ApiResponse.failure(code.getCode(), message));
    }

    protected String createErrorResponse(String message) {
        return createErrorResponse(ApiCode.GENERIC_ERROR, message);
    }

    protected void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String content) {
        ByteBuf buffer = Unpooled.copiedBuffer(content, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buffer.readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
