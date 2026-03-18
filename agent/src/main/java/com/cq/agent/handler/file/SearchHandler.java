package com.cq.agent.handler.file;

import com.cq.agent.dto.ApiResponse;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.model.FileInfo;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

import java.util.List;

public class SearchHandler extends BaseHandler {

    public SearchHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", ".");
        String pattern = getQueryParam(request, "pattern", "*");
        int maxDepth = Integer.parseInt(getQueryParam(request, "maxDepth", "10"));
        ApiResponse<List<FileInfo>> result = fileService.search(path, pattern, maxDepth);
        sendServiceResult(ctx,request, result);
    }
}
