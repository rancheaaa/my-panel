package com.cq.agent.handler;

import com.cq.agent.dto.ApiCode;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

/**
 * HTTP handler for file operations (FTP-like commands).
 */
public class FileHandler extends CommonNettyHandler {

    private static final Logger logger = LoggerFactory.getLogger(FileHandler.class);

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
        
        ctx.channel().attr(TRACE_ID_KEY).set(traceid);
        ctx.channel().attr(REQUEST_URI_KEY).set(request.uri() + " " + request.method().toString());

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
                        createErrorResponse(ApiCode.INTERNAL_SERVER_ERROR, "Internal error: " + e.getMessage()));
            }
        } finally {
            // 不再使用MDC，traceid通过手工打印传递
        }
    }
}