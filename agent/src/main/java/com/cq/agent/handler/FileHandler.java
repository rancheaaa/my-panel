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
        // Get traceid from HTTP header
        String traceid = request.headers().get("X-Trace-Id");
        if (traceid == null || traceid.isEmpty()) {
            traceid = UUID.randomUUID().toString();
        }

        try {
            if (!request.decoderResult().isSuccess()) {
                sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid request"));
                return;
            }

            String uri = request.uri();
            // Remove query string for path matching
            int queryIndex = uri.indexOf('?');
            String path = queryIndex > 0 ? uri.substring(0, queryIndex) : uri;
            HttpMethod method = request.method();

            logger.debug("[traceId={}] File request: {} {}", traceid, method, uri);
            try {
                IRequestHandler handler = handlerFactory.getHandler(path);
                if (handler != null) {
                    handler.handle(ctx, request);
                } else {
                    ctx.fireChannelRead(request.retain());
                }
            } catch (Exception e) {
                logger.error("[traceId={}] Error processing file request: ", traceid, e);
                sendResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        createErrorResponse("Internal error: " + e.getMessage()));
            }
        } finally {
            // 不再使用MDC，traceid通过手工打印传递
        }
    }

    private String createErrorResponse(ApiCode code, String message) {
        return gson.toJson(ApiResponse.failure(code.getCode(), message));
    }

    private String createErrorResponse(String message) {
        return createErrorResponse(ApiCode.GENERIC_ERROR, message);
    }

    private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, String content) {
        ByteBuf buffer = Unpooled.copiedBuffer(content, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buffer.readableBytes());

        // Handle keep-alive
        boolean keepAlive = HttpUtil.isKeepAlive(request);
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
        logger.info("Sending response: status={}, content={}, keepAlive={}, connectionId={}, localPort={}, remoteAddress={}:{}",
                status, content, keepAlive, connectionId, localPort, remoteAddress, remotePort);

        if (keepAlive) {
            ctx.writeAndFlush(response);
        } else {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String content) {
        // For exception cases where we don't have the original request
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