package com.cq.agent.handler.file;

import com.cq.agent.dto.ApiCode;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class DirCheckHandler extends BaseHandler {

    private static final long MIN_DISK_SPACE_MB = 100;

    public DirCheckHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        super(fileService, chunkedTransferService);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null || path.isEmpty()) {
            sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST,
                    createErrorResponse(ApiCode.INVALID_REQUEST, "'path' parameter is required"));
            return;
        }

        Map<String, Object> result = checkDirectory(path);
        sendSuccessResponse(ctx, request, result);
    }

    private Map<String, Object> checkDirectory(String path) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);

        try {
            Path targetPath = fileService.resolvePathPublic(path);

            boolean exists = Files.exists(targetPath);
            result.put("exists", exists);

            if (!exists) {
                result.put("isDirectory", false);
                result.put("canRead", false);
                result.put("canWrite", false);
                result.put("canExecute", false);
                result.put("diskTotal", 0L);
                result.put("diskUsable", 0L);
                result.put("diskFree", 0L);
                result.put("diskSufficient", false);
                result.put("errorMessage", "目录不存在");
                return result;
            }

            boolean isDirectory = Files.isDirectory(targetPath);
            result.put("isDirectory", isDirectory);

            boolean canRead = Files.isReadable(targetPath);
            boolean canWrite = Files.isWritable(targetPath);
            boolean canExecute = Files.isExecutable(targetPath);
            result.put("canRead", canRead);
            result.put("canWrite", canWrite);
            result.put("canExecute", canExecute);

            try {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(targetPath);
                result.put("posixPermissions", PosixFilePermissions.toString(perms));
            } catch (Exception e) {
                File f = targetPath.toFile();
                result.put("posixPermissions", (f.canRead() ? "r" : "-") + (f.canWrite() ? "w" : "-") + (f.canExecute() ? "x" : "-"));
            }

            try {
                FileStore store = Files.getFileStore(targetPath);
                long total = store.getTotalSpace();
                long usable = store.getUsableSpace();
                long free = store.getUnallocatedSpace();
                result.put("diskTotal", total);
                result.put("diskUsable", usable);
                result.put("diskFree", free);
                result.put("diskTotalMB", total / (1024 * 1024));
                result.put("diskUsableMB", usable / (1024 * 1024));
                result.put("diskFreeMB", free / (1024 * 1024));
                result.put("diskSufficient", usable >= MIN_DISK_SPACE_MB * 1024 * 1024);
            } catch (IOException e) {
                result.put("diskTotal", 0L);
                result.put("diskUsable", 0L);
                result.put("diskFree", 0L);
                result.put("diskSufficient", false);
                result.put("diskError", e.getMessage());
            }

        } catch (SecurityException e) {
            result.put("exists", false);
            result.put("canRead", false);
            result.put("canWrite", false);
            result.put("canExecute", false);
            result.put("diskSufficient", false);
            result.put("errorMessage", "访问被拒绝: " + e.getMessage());
        } catch (Exception e) {
            result.put("exists", false);
            result.put("canRead", false);
            result.put("canWrite", false);
            result.put("canExecute", false);
            result.put("diskSufficient", false);
            result.put("errorMessage", "检测失败: " + e.getMessage());
        }

        return result;
    }
}
