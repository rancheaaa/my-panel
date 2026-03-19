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
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * @author cq 2026/3/19 21:23
 */
public abstract class CommonNettyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(CommonNettyHandler.class);
    protected static final AttributeKey<String> TRACE_ID_KEY = AttributeKey.valueOf("traceId");
    protected static final AttributeKey<String> REQUEST_URI_KEY = AttributeKey.valueOf("requestUri");
    private static final Gson gson = new Gson();

    protected String createErrorResponse(ApiCode code, String message) {
        return gson.toJson(ApiResponse.failure(code.getCode(), message));
    }

    protected String createErrorResponse(String message) {
        return createErrorResponse(ApiCode.GENERIC_ERROR, message);
    }

    protected String getTraceId(ChannelHandlerContext ctx) {
        String traceId = ctx.channel().attr(TRACE_ID_KEY).get();
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }
        return traceId;
    }

    @SuppressWarnings("all")
    protected void sendResponse(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, String content) {
        String traceId = request != null ? request.headers().get("X-Trace-Id") : null;
        if (traceId == null || traceId.isEmpty()) {
            traceId = ctx.channel().attr(TRACE_ID_KEY).get();
        }

        ByteBuf buffer = Unpooled.copiedBuffer(content, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buffer.readableBytes());

        // Handle keep-alive
        boolean keepAlive = false;
        if(request != null) {
            keepAlive = HttpUtil.isKeepAlive(request);
            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            } else {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            }
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
        logger.info("[traceId={}] Sending response: uri={}, method={}, status={}, content={}, keepAlive={}, connectionId={}, localPort={}, remoteAddress={}:{}",
                traceId, request == null ? ctx.channel().attr(REQUEST_URI_KEY).get() : request.uri(), request == null ? "N/A" : request.method().toString(), status, content, keepAlive, connectionId, localPort, remoteAddress, remotePort);

        if (keepAlive) {
            ctx.writeAndFlush(response);
        } else {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (isNormalConnectionClose(cause)) {
            logger.debug("Normal connection close in file handler: {}", cause.getMessage());
        } else {
            logger.error("Exception caught in file handler", cause);
            if (ctx.channel().isActive()) {
                sendResponse(ctx, null, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        createErrorResponse(ApiCode.INTERNAL_SERVER_ERROR, "Internal server error"));
            }
        }
    }

    private boolean isNormalConnectionClose(Throwable cause) {
        if (cause instanceof java.net.SocketException) {
            String message = cause.getMessage();
            if (message != null) {
                String lowerMessage = message.toLowerCase();
                return lowerMessage.contains("connection reset") ||
                        lowerMessage.contains("connection closed") ||
                        lowerMessage.contains("broken pipe") ||
                        lowerMessage.contains("connection aborted");
            }
        }
        return false;
    }
}