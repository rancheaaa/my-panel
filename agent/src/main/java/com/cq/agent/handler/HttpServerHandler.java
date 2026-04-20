package com.cq.agent.handler;

import com.cq.agent.dto.ApiCode;
import com.cq.panel.common.dto.agent.AgentExecuteCommandRequest;
import com.cq.panel.common.dto.agent.AgentExecuteCommandResponse;
import com.cq.panel.common.dto.agent.HealthBeatResponse;
import com.cq.agent.executor.CommandExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HTTP request handler for command execution API.
 * Supports both Windows and Linux/Unix systems.
 */
public class HttpServerHandler extends CommonNettyHandler {

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
        
        ctx.channel().attr(TRACE_ID_KEY).set(traceid);
        ctx.channel().attr(REQUEST_URI_KEY).set(request.uri() + " " + request.method().toString());

        try {
            if (!request.decoderResult().isSuccess()) {
                sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid request"));
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
                sendResponse(ctx, request, HttpResponseStatus.NOT_FOUND, createErrorResponse(ApiCode.NOT_FOUND, "Endpoint not found"));
            }
        } finally {
            // 不再使用MDC，traceid通过手工打印传递
        }
    }

    private void handleHealthCheck(ChannelHandlerContext ctx, FullHttpRequest request) {
        HealthBeatResponse resp = new HealthBeatResponse();
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
            sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "Request body is required"));
            return;
        }

        try {
            AgentExecuteCommandRequest requestJson = gson.fromJson(body, AgentExecuteCommandRequest.class);
            if (requestJson == null || requestJson.getCommand() == null || requestJson.getCommand().isBlank()) {
                sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "'command' field is required"));
                return;
            }

            long timeout;
            if (requestJson.getTimeout() != null && requestJson.getTimeout() > 0) {
                if (requestJson.getTimeout() > commandExecutor.getMaxTimeoutSeconds()) {
                    sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST,
                            createErrorResponse(ApiCode.INVALID_REQUEST, "Timeout must be between 1 and " + commandExecutor.getMaxTimeoutSeconds() + " seconds"));
                    return;
                }
                timeout = requestJson.getTimeout();
            } else {
                timeout = commandExecutor.getDefaultTimeoutSeconds();
            }

            AgentExecuteCommandResponse resp = commandExecutor.execute(requestJson.getCommand(), timeout);

            sendResponse(ctx, request, HttpResponseStatus.OK, gson.toJson(resp));

        } catch (JsonSyntaxException e) {
            sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid JSON format"));
        } catch (Exception e) {
            logger.error("[traceId={}] Error executing command", getTraceId(ctx), e);
            sendResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    createErrorResponse(ApiCode.INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage()));
        }
    }
}