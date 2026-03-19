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
import io.netty.util.AttributeKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseHandler implements IRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(BaseHandler.class);
    private static final AttributeKey<String> TRACE_ID_KEY = AttributeKey.valueOf("traceId");
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

    protected <T> void sendServiceResult(ChannelHandlerContext ctx, FullHttpRequest request, ApiResponse<T> result) {
        if (result.isSuccess()) {
            sendSuccessResponse(ctx, request, result.getData());
        } else {
            sendResponse(ctx, request, HttpResponseStatus.OK, gson.toJson(result));
        }
    }

    protected void sendSuccessResponse(ChannelHandlerContext ctx, FullHttpRequest request, Object data) {
        sendResponse(ctx, request, HttpResponseStatus.OK, gson.toJson(ApiResponse.success(data)));
    }

    protected String createErrorResponse(ApiCode code, String message) {
        return gson.toJson(ApiResponse.failure(code.getCode(), message));
    }

    protected String createErrorResponse(String message) {
        return createErrorResponse(ApiCode.GENERIC_ERROR, message);
    }

    protected void sendResponse(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, String content) {
        String traceId = request != null ? request.headers().get("X-Trace-Id") : null;
        if (traceId == null || traceId.isEmpty()) {
            traceId = "N/A";
        }
        
        ctx.channel().attr(TRACE_ID_KEY).set(traceId);
        
        ByteBuf buffer = Unpooled.copiedBuffer(content, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buffer.readableBytes());
        
        // Handle keep-alive
        boolean keepAlive = request != null && HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        } else {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }
        
        // Get connection information
        String remoteAddress = ctx.channel().remoteAddress().toString();
        int localPort = ctx.channel().localAddress() instanceof java.net.InetSocketAddress ? 
            ((java.net.InetSocketAddress) ctx.channel().localAddress()).getPort() : 0;
        int remotePort = ctx.channel().remoteAddress() instanceof java.net.InetSocketAddress ? 
            ((java.net.InetSocketAddress) ctx.channel().remoteAddress()).getPort() : 0;
        String connectionId = ctx.channel().id().asShortText();
        
        // Log response details
        logger.info("[traceId={}] Sending response: status={}, content={}, keepAlive={}, connectionId={}, localPort={}, remoteAddress={}:{}", 
            traceId, status, content, keepAlive, connectionId, localPort, remoteAddress, remotePort);
        
        if (keepAlive) {
            ctx.writeAndFlush(response);
        } else {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }
}