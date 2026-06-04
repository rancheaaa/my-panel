package com.cq.agent.handler.batch;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.handler.IRequestHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ConfigPushHandler implements IRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfigPushHandler.class);
    private static final Gson GSON = new Gson();
    private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000; // 5分钟超时

    private final ConfigFileManager configFileManager;
    private final ConcurrentHashMap<String, PushSession> sessions = new ConcurrentHashMap<>();

    public ConfigPushHandler(ConfigFileManager configFileManager) {
        this.configFileManager = configFileManager;
        // 启动超时清理线程
        Thread cleanupThread = new Thread(this::cleanupExpiredSessions, "config-push-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String uri = request.uri().split("\\?")[0];
            String body = request.content().toString(StandardCharsets.UTF_8);

            if (uri.endsWith("/push/init")) {
                handleInit(ctx, request, body);
            } else if (uri.endsWith("/push/chunk")) {
                handleChunk(ctx, request, body);
            } else if (uri.endsWith("/push/complete")) {
                handleComplete(ctx, request, body);
            } else {
                sendResponse(ctx, request, HttpResponseStatus.NOT_FOUND,
                        ApiResponse.failure(404, "Unknown endpoint"));
            }
        } catch (Exception e) {
            log.error("配置推送处理失败", e);
            sendResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    ApiResponse.failure(500, "配置推送处理失败: " + e.getMessage()));
        }
    }

    private void handleInit(ChannelHandlerContext ctx, FullHttpRequest request, String body) {
        JsonObject params = JsonParser.parseString(body).getAsJsonObject();
        long totalSize = params.get("totalSize").getAsLong();
        int totalChunks = params.get("totalChunks").getAsInt();
        long chunkSize = params.get("chunkSize").getAsLong();

        String sessionId = UUID.randomUUID().toString().replace("-", "");
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("config-push-" + sessionId);
        } catch (IOException e) {
            log.error("创建临时目录失败", e);
            sendResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    ApiResponse.failure(500, "创建临时目录失败"));
            return;
        }

        PushSession session = new PushSession();
        session.sessionId = sessionId;
        session.totalSize = totalSize;
        session.totalChunks = totalChunks;
        session.chunkSize = chunkSize;
        session.tempDir = tempDir;
        session.receivedChunks = new boolean[totalChunks];
        session.createdAt = System.currentTimeMillis();

        sessions.put(sessionId, session);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        sendResponse(ctx, request, HttpResponseStatus.OK, ApiResponse.success(data));
    }

    private void handleChunk(ChannelHandlerContext ctx, FullHttpRequest request, String body) {
        JsonObject params = JsonParser.parseString(body).getAsJsonObject();
        String sessionId = params.get("sessionId").getAsString();
        int chunkIndex = params.get("chunkIndex").getAsInt();
        String base64Data = params.get("data").getAsString();

        PushSession session = sessions.get(sessionId);
        if (session == null) {
            sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST,
                    ApiResponse.failure(400, "会话不存在或已过期"));
            return;
        }

        try {
            byte[] chunkData = Base64.getDecoder().decode(base64Data);
            Path chunkFile = session.tempDir.resolve("chunk_" + chunkIndex);
            Files.write(chunkFile, chunkData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            session.receivedChunks[chunkIndex] = true;
        } catch (IOException e) {
            log.error("写入分片失败: sessionId={}, chunkIndex={}", sessionId, chunkIndex, e);
            sendResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    ApiResponse.failure(500, "写入分片失败"));
            return;
        }

        int receivedCount = 0;
        for (boolean received : session.receivedChunks) {
            if (received) receivedCount++;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("receivedChunks", receivedCount);
        sendResponse(ctx, request, HttpResponseStatus.OK, ApiResponse.success(data));
    }

    private void handleComplete(ChannelHandlerContext ctx, FullHttpRequest request, String body) throws Exception {
        JsonObject params = JsonParser.parseString(body).getAsJsonObject();
        String sessionId = params.get("sessionId").getAsString();

        PushSession session = sessions.remove(sessionId);
        if (session == null) {
            sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST,
                    ApiResponse.failure(400, "会话不存在或已过期"));
            return;
        }

        // 检查所有分片是否已接收
        for (boolean received : session.receivedChunks) {
            if (!received) {
                cleanupSession(session);
                sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST,
                        ApiResponse.failure(400, "部分分片未接收"));
                return;
            }
        }

        // 合并分片为 zip
        Path zipFile = session.tempDir.resolve("config.zip");
        try (OutputStream out = Files.newOutputStream(zipFile)) {
            for (int i = 0; i < session.totalChunks; i++) {
                Path chunkFile = session.tempDir.resolve("chunk_" + i);
                Files.copy(chunkFile, out);
            }
        }

        // 删除旧配置文件
        File configDir = configFileManager.getConfigDir();
        if (configDir != null && configDir.exists()) {
            File[] oldFiles = configDir.listFiles((dir, name) ->
                    name.equals("tasks.meta.json") || (name.startsWith("task_") && name.endsWith(".json")));
            if (oldFiles != null) {
                for (File oldFile : oldFiles) {
                    oldFile.delete();
                }
            }
        }

        // 解压新配置到 configDir
        int filesCount = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path targetPath = configDir.toPath().resolve(entry.getName());
                // 安全检查：防止路径遍历
                if (!targetPath.normalize().startsWith(configDir.toPath().normalize())) {
                    log.warn("跳过路径遍历条目: {}", entry.getName());
                    continue;
                }
                Files.createDirectories(targetPath.getParent());
                Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                filesCount++;
                zis.closeEntry();
            }
        }

        // 通知 ConfigFileManager 重新加载
        configFileManager.reloadFromDisk();

        // 清理临时目录
        cleanupSession(session);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("filesCount", filesCount);
        sendResponse(ctx, request, HttpResponseStatus.OK, ApiResponse.success(data));
    }

    private void cleanupSession(PushSession session) {
        try {
            if (session.tempDir != null) {
                File[] files = session.tempDir.toFile().listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
                Files.deleteIfExists(session.tempDir);
            }
        } catch (Exception e) {
            log.warn("清理临时目录失败: {}", session.tempDir, e);
        }
    }

    private void cleanupExpiredSessions() {
        while (true) {
            try {
                Thread.sleep(60000); // 每分钟检查一次
                long now = System.currentTimeMillis();
                sessions.entrySet().removeIf(entry -> {
                    if (now - entry.getValue().createdAt > SESSION_TIMEOUT_MS) {
                        cleanupSession(entry.getValue());
                        log.info("清理过期推送会话: sessionId={}", entry.getKey());
                        return true;
                    }
                    return false;
                });
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest request,
                              HttpResponseStatus status, Object data) {
        String content = GSON.toJson(data);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer(content, StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response);
        } else {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static class PushSession {
        String sessionId;
        long totalSize;
        int totalChunks;
        long chunkSize;
        Path tempDir;
        boolean[] receivedChunks;
        long createdAt;
    }
}
