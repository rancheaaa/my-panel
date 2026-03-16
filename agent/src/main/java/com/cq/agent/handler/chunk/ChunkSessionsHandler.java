package com.cq.agent.handler.chunk;

import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

import java.util.List;
import java.util.Map;

public class ChunkSessionsHandler extends BaseHandler {

    public ChunkSessionsHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        List<Map<String, Object>> sessions = chunkedTransferService.listUploadSessions();
        sendSuccessResponse(ctx, sessions);
    }
}
