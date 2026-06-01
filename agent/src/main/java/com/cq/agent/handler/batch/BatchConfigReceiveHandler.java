package com.cq.agent.handler.batch;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.dto.ApiCode;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 批量传输任务配置接收Handler
 * 接收Proxy推送的任务配置并持久化到本地
 * POST /api/batch/task/config
 */
public class BatchConfigReceiveHandler extends BaseHandler {

    private static final Logger log = LoggerFactory.getLogger(BatchConfigReceiveHandler.class);

    private final ConfigFileManager configFileManager;
    private final ConfigChangeListener configChangeListener;

    public BatchConfigReceiveHandler(FileService fileService, ChunkedTransferService chunkedTransferService,
                                     ConfigFileManager configFileManager, ConfigChangeListener configChangeListener) {
        super(fileService, chunkedTransferService);
        this.configFileManager = configFileManager;
        this.configChangeListener = configChangeListener;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!HttpMethod.POST.equals(request.method())) {
            sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED,
                createErrorResponse(ApiCode.INVALID_REQUEST, "Only POST method is supported"));
            return;
        }

        try {
            AgentTaskConfig config = parseBody(request, AgentTaskConfig.class);

            if (config == null || config.getTaskId() == null) {
                sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST,
                    createErrorResponse(ApiCode.INVALID_PARAMETER, "Invalid config: taskId is required"));
                return;
            }

            log.info("收到任务配置推送: taskId={}, taskName={}, version={}",
                config.getTaskId(), config.getTaskName(), config.getVersion());

            boolean hasChange = configChangeListener.detectAndApplyChange(config);

            Map<String, Object> result = new HashMap<>();
            result.put("configPersisted", true);
            result.put("taskId", config.getTaskId());
            result.put("receivedAt", System.currentTimeMillis());
            result.put("version", config.getVersion());
            result.put("changed", hasChange);

            sendSuccessResponse(ctx, request, result);

            log.info("配置接收成功: taskId={}, configPersisted=true, changed={}", config.getTaskId(), hasChange);

        } catch (Exception e) {
            log.error("配置接收失败: {}", e.getMessage(), e);
            sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR,
                createErrorResponse(ApiCode.INTERNAL_SERVER_ERROR, "Config receive failed: " + e.getMessage()));
        }
    }
}
