package com.cq.agent.handler.batch;

import com.cq.agent.batch.postprocess.PostTransferConfig;
import com.cq.agent.batch.postprocess.PostTransferHandler;
import com.cq.agent.batch.postprocess.PostProcessResult;
import com.cq.agent.dto.ApiCode;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import java.util.List;
import java.util.Map;

public class BatchPostProcessHandler extends BaseHandler
{
    private static final Gson gson = new Gson();
    private final PostTransferHandler postTransferHandler;

    public BatchPostProcessHandler(FileService fileService, ChunkedTransferService chunkedTransferService, PostTransferHandler postTransferHandler)
    {
        super(fileService, chunkedTransferService);
        this.postTransferHandler = postTransferHandler;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request)
    {
        String body = request.content().toString(java.nio.charset.StandardCharsets.UTF_8);
        if (body == null || body.isEmpty())
        {
            sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST,
                    createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid request body"));
            return;
        }
        Map<String, Object> req = gson.fromJson(body, new TypeToken<Map<String, Object>>(){}.getType());
        if (req == null)
        {
            sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST,
                    createErrorResponse(ApiCode.INVALID_REQUEST, "Invalid request body"));
            return;
        }
        Long taskId = req.get("taskId") != null ? ((Number) req.get("taskId")).longValue() : null;
        List<String> successFiles = (List<String>) req.get("successFiles");
        PostTransferConfig config = new PostTransferConfig();
        config.setAction((String) req.getOrDefault("action", "NONE"));
        config.setSourceBaseDir((String) req.get("sourceBaseDir"));
        config.setBackupDir((String) req.get("backupDir"));
        config.setBackupMode((String) req.getOrDefault("backupMode", "COPY"));
        config.setPreserveDirStructure(Boolean.TRUE.equals(req.get("preserveDirStructure")));
        PostProcessResult result = postTransferHandler.execute(taskId, successFiles != null ? successFiles : List.of(), config);
        sendSuccessResponse(ctx, request, result);
    }
}
