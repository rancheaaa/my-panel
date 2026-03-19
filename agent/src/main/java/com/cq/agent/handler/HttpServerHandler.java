package com.cq.agent.handler;

import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.ExecuteRequest;
import com.cq.agent.dto.ExecuteResponse;
import com.cq.agent.dto.HealthResponse;
import com.cq.agent.executor.CommandExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
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
 * HTTP request handler for command execution API.
 * Supports both Windows and Linux/Unix systems.
 */
public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(HttpServerHandler.class);
    private static final Gson gson = new Gson();
    private static final String EXECUTE_PATH = "/api/execute";
    private static final String HEALTH_PATH = "/api/health";

    private final CommandExecutor commandExecutor;

    public HttpServerHandler(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
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
                sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid request"));
                return;
            }

            String uri = request.uri();
            HttpMethod method = request.method();

            logger.debug("[traceId={}] Received request: {} {}", traceid, method, uri);

            if (uri.equals(HEALTH_PATH) && method == HttpMethod.GET) {
                handleHealthCheck(ctx, request);
            } else if (uri.equals(EXECUTE_PATH) && method == HttpMethod.POST) {
                handleExecute(ctx, request);
            } else {
                sendResponse(ctx, request, HttpResponseStatus.NOT_FOUND, createErrorResponse("Endpoint not found"));
            }
        } finally {
            // 不再使用MDC，traceid通过手工打印传递
        }
    }

    private void handleHealthCheck(ChannelHandlerContext ctx, FullHttpRequest request) {
        HealthResponse resp = new HealthResponse();
        resp.setStatus("UP");
        resp.setOs(commandExecutor.getOsName());
        resp.setOsType(commandExecutor.isWindows() ? "windows" : "unix");
        resp.setDefaultTimeout(commandExecutor.getDefaultTimeoutSeconds());
        resp.setMaxTimeout(commandExecutor.getMaxTimeoutSeconds());
        sendResponse(ctx, request, HttpResponseStatus.OK, gson.toJson(resp));
    }

    private void handleExecute(ChannelHandlerContext ctx, FullHttpRequest request) {
        // Parse request body
        ByteBuf content = request.content();
        String body = content.toString(StandardCharsets.UTF_8);

        if (body.isEmpty()) {
            sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Request body is required"));
            return;
        }

        try {
            ExecuteRequest requestJson = gson.fromJson(body, ExecuteRequest.class);
            if (requestJson == null || requestJson.getCommand() == null || requestJson.getCommand().isBlank()) {
                sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST.getCode(), "'command' field is required"));
                return;
            }

            long timeout;
            if (requestJson.getTimeout() != null && requestJson.getTimeout() > 0) {
                if (requestJson.getTimeout() > commandExecutor.getMaxTimeoutSeconds()) {
                    sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST,
                            createErrorResponse("Timeout must be between 1 and " + commandExecutor.getMaxTimeoutSeconds() + " seconds"));
                    return;
                }
                timeout = requestJson.getTimeout();
            } else {
                timeout = commandExecutor.getDefaultTimeoutSeconds();
            }

            CommandExecutor.CommandResult result = commandExecutor.execute(requestJson.getCommand(), timeout);

            ExecuteResponse resp = new ExecuteResponse();
            resp.setSuccess(result.isSuccess());
            resp.setExitCode(result.exitCode());
            resp.setOutput(result.output());
            resp.setError(result.error());

            sendResponse(ctx, request, HttpResponseStatus.OK, gson.toJson(resp));

        } catch (JsonSyntaxException e) {
            logger.warn("Invalid JSON in request body", e);
            sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST.getCode(), "Invalid JSON format"));
        } catch (Exception e) {
            logger.error("Error processing request", e);
            sendResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    private String createErrorResponse(int code, String message) {
        return gson.toJson(ApiResponse.failure(code, message));
    }

    private String createErrorResponse(String message) {
        return gson.toJson(ApiResponse.failure(message));
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
        logger.error("Exception caught in handler", cause);
        if (ctx.channel().isActive()) {
            sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    createErrorResponse(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Internal server error"));
        }
    }
}