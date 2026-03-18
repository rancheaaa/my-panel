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
import org.slf4j.MDC;
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
        // Generate or get traceid
        String traceid = request.headers().get("X-Trace-Id");
        if (traceid == null || traceid.isEmpty()) {
            traceid = UUID.randomUUID().toString();
        }
        MDC.put("traceid", traceid);
        
        try {
            if (!request.decoderResult().isSuccess()) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid request"));
                return;
            }

            String uri = request.uri();
            HttpMethod method = request.method();

            logger.debug("Received request: {} {}", method, uri);

            if (uri.equals(HEALTH_PATH) && method == HttpMethod.GET) {
                handleHealthCheck(ctx);
            } else if (uri.equals(EXECUTE_PATH) && method == HttpMethod.POST) {
                handleExecute(ctx, request);
            } else {
                sendResponse(ctx, HttpResponseStatus.NOT_FOUND, createErrorResponse("Endpoint not found"));
            }
        } finally {
            MDC.clear();
        }
    }

    private void handleHealthCheck(ChannelHandlerContext ctx) {
        HealthResponse resp = new HealthResponse();
        resp.setStatus("UP");
        resp.setOs(commandExecutor.getOsName());
        resp.setOsType(commandExecutor.isWindows() ? "windows" : "unix");
        resp.setDefaultTimeout(commandExecutor.getDefaultTimeoutSeconds());
        resp.setMaxTimeout(commandExecutor.getMaxTimeoutSeconds());
        sendResponse(ctx, HttpResponseStatus.OK, gson.toJson(resp));
    }

    private void handleExecute(ChannelHandlerContext ctx, FullHttpRequest request) {
        // Parse request body
        ByteBuf content = request.content();
        String body = content.toString(StandardCharsets.UTF_8);

        if (body.isEmpty()) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Request body is required"));
            return;
        }

        try {
            ExecuteRequest requestJson = gson.fromJson(body, ExecuteRequest.class);
            if (requestJson == null || requestJson.getCommand() == null || requestJson.getCommand().isBlank()) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST.getCode(), "'command' field is required"));
                return;
            }

            long timeout;
            if (requestJson.getTimeout() != null && requestJson.getTimeout() > 0) {
                if (requestJson.getTimeout() > commandExecutor.getMaxTimeoutSeconds()) {
                    sendResponse(ctx, HttpResponseStatus.BAD_REQUEST,
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

            sendResponse(ctx, HttpResponseStatus.OK, gson.toJson(resp));

        } catch (JsonSyntaxException e) {
            logger.warn("Invalid JSON in request body", e);
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST.getCode(), "Invalid JSON format"));
        } catch (Exception e) {
            logger.error("Error processing request", e);
            sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    private String createErrorResponse(int code, String message) {
        return gson.toJson(ApiResponse.failure(code, message));
    }

    private String createErrorResponse(String message) {
        return gson.toJson(ApiResponse.failure(message));
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
        logger.error("Exception caught in handler", cause);
        if (ctx.channel().isActive()) {
            sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    createErrorResponse(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Internal server error"));
        }
    }
}