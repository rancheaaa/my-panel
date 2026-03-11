package com.cq.agent.handler;

import com.cq.agent.executor.CommandExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
    }

    private void handleHealthCheck(ChannelHandlerContext ctx) {
        JsonObject response = new JsonObject();
        response.addProperty("status", "UP");
        response.addProperty("os", commandExecutor.getOsName());
        response.addProperty("osType", commandExecutor.isWindows() ? "windows" : "unix");
        response.addProperty("defaultTimeout", commandExecutor.getDefaultTimeoutSeconds());
        response.addProperty("maxTimeout", commandExecutor.getMaxTimeoutSeconds());
        sendResponse(ctx, HttpResponseStatus.OK, gson.toJson(response));
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
            JsonObject requestJson = gson.fromJson(body, JsonObject.class);

            if (!requestJson.has("command") || requestJson.get("command").isJsonNull()) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'command' field is required"));
                return;
            }

            String command = requestJson.get("command").getAsString();

            // Use default timeout from config if not specified in request
            long timeout;
            if (requestJson.has("timeout") && !requestJson.get("timeout").isJsonNull()) {
                timeout = requestJson.get("timeout").getAsLong();
                if (timeout <= 0) {
                    timeout = commandExecutor.getDefaultTimeoutSeconds();
                } else if (timeout > commandExecutor.getMaxTimeoutSeconds()) {
                    sendResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                            createErrorResponse("Timeout must be between 1 and " + commandExecutor.getMaxTimeoutSeconds() + " seconds"));
                    return;
                }
            } else {
                // Use default timeout from configuration
                timeout = commandExecutor.getDefaultTimeoutSeconds();
            }

            // Execute command
            CommandExecutor.CommandResult result = commandExecutor.execute(command, timeout);

            JsonObject response = new JsonObject();
            response.addProperty("success", result.isSuccess());
            response.addProperty("exitCode", result.exitCode());
            response.addProperty("output", result.output());
            if (result.error() != null) {
                response.addProperty("error", result.error());
            }

            sendResponse(ctx, HttpResponseStatus.OK, gson.toJson(response));

        } catch (JsonSyntaxException e) {
            logger.warn("Invalid JSON in request body", e);
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        } catch (Exception e) {
            logger.error("Error processing request", e);
            sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    private String createErrorResponse(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("error", message);
        return gson.toJson(error);
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
                    createErrorResponse("Internal server error"));
        }
    }
}
