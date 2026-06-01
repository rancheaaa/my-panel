package com.cq.agent.handler;

import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.Gson;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProbeHandler extends BaseHandler {

    private static final Logger logger = LoggerFactory.getLogger(ProbeHandler.class);
    private static final int CONNECT_TIMEOUT_MS = 5000;

    public ProbeHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String targetHost = getQueryParam(request, "host", null);
        String targetPortStr = getQueryParam(request, "port", null);

        if (targetHost == null || targetHost.isEmpty()) {
            sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST,
                    createErrorResponse(ApiCode.INVALID_PARAMETER, "参数 host 不能为空"));
            return;
        }
        if (targetPortStr == null || targetPortStr.isEmpty()) {
            sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST,
                    createErrorResponse(ApiCode.INVALID_PARAMETER, "参数 port 不能为空"));
            return;
        }

        int targetPort;
        try {
            targetPort = Integer.parseInt(targetPortStr);
        } catch (NumberFormatException e) {
            sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST,
                    createErrorResponse(ApiCode.INVALID_PARAMETER, "参数 port 必须是有效端口号"));
            return;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetHost", targetHost);
        result.put("targetPort", targetPort);

        List<String> details = new ArrayList<>();
        boolean reachable = probePort(targetHost, targetPort, details);

        result.put("reachable", reachable);
        result.put("details", details);

        sendSuccessResponse(ctx, request, result);
    }

    private boolean probePort(String host, int port, List<String> details) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            details.add("✅ 端口 " + host + ":" + port + " 可达");
            return true;
        } catch (IOException e) {
            String reason = classifyFailure(e);
            details.add("❌ 端口 " + host + ":" + port + " 不可达 - " + reason);
            logger.warn("探测端口连通性失败 {}:{} - {}", host, port, reason);
            return false;
        }
    }

    private String classifyFailure(IOException e) {
        String msg = e.getMessage();
        if (msg == null) return "未知原因";
        String lower = msg.toLowerCase();
        if (lower.contains("connection refused")) return "连接被拒绝（目标进程未启动或端口未监听）";
        if (lower.contains("network is unreachable") || lower.contains("no route to host")) return "网络不可达（可能因网络策略限制）";
        if (lower.contains("timed out")) return "连接超时（可能因防火墙拦截或网络不通）";
        if (lower.contains("connection reset")) return "连接被重置（对端拒绝连接）";
        if (lower.contains("unreachable")) return "目标主机不可达";
        if (lower.contains("permission denied")) return "权限被拒绝（可能因安全策略限制）";
        return "未知原因: " + msg;
    }
}
