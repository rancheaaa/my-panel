package com.cq.agent.handler;

import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ApiResponse;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HTTP handler for file operations (FTP-like commands).
 */
public class FileHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(FileHandler.class);
    private static final Gson gson = new Gson();

    private final HandlerFactory handlerFactory;

    public FileHandler(HandlerFactory handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        // Generate or get traceid
        String traceid = request.headers().get("X-Trace-Id");
        if (traceid == null || traceid.isEmpty()) {
            traceid = UUID.randomUUID().toString();
        }
        MDC.put("traceid", traceid);
        
        try {
            if (!request.decoderResult().isSuccess()) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid request"));
                return;
            }

            String uri = request.uri();
            // Remove query string for path matching
            int queryIndex = uri.indexOf('?');
            String path = queryIndex > 0 ? uri.substring(0, queryIndex) : uri;
            HttpMethod method = request.method();

            logger.debug("File request: {} {}", method, uri);
            try {
                IRequestHandler handler = handlerFactory.getHandler(path);
                if (handler != null) {
                    handler.handle(ctx, request);
                } else {
                    ctx.fireChannelRead(request.retain());
                }
            } catch (Exception e) {
                logger.error("Error processing file request", e);
                sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        createErrorResponse("Internal error: " + e.getMessage()));
            }
        } finally {
            MDC.clear();
        }
    }

    private String createErrorResponse(ApiCode code, String message) {
        return gson.toJson(ApiResponse.failure(code.getCode(), message));
    }

    private String createErrorResponse(String message) {
        return createErrorResponse(ApiCode.GENERIC_ERROR, message);
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String content) {
        ByteBuf buffer = Unpooled.copiedBuffer(content, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buffer.readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception caught in file handler", cause);
        if (ctx.channel().isActive()) {
            sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    createErrorResponse("Internal server error"));
        }
    }
}