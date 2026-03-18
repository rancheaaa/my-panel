package com.cq.agent.handler.chunk;

import com.cq.agent.dto.ChunkStatusData;
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
        java.util.List<ChunkStatusData> sessions = chunkedTransferService.listUploadSessions();
        sendSuccessResponse(ctx, sessions);
    }
}
