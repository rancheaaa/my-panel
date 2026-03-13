package com.cq.agent.client.upload;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.AccessDeniedException;
import java.util.Locale;

/**
 * Classifies upload errors into user-friendly messages for:
 * - Receiver unreachable (process down, port closed)
 * - Disk full on receiver
 * - Permission denied (local read / remote write)
 */
final class UploadErrorClassifier {

    private UploadErrorClassifier() {}

    /**
     * @return true if retrying the same request will not help (e.g. connection refused).
     */
    static boolean isNonRetryable(Throwable t) {
        Throwable c = t;
        while (c != null) {
            if (c instanceof ConnectException || c instanceof UnknownHostException) {
                return true;
            }
            if (c instanceof SocketTimeoutException) {
                String msg = c.getMessage() != null ? c.getMessage().toLowerCase(Locale.ROOT) : "";
                if (msg.contains("connect timed out") || msg.contains("connection timed out")) {
                    return true;
                }
            }
            c = c.getCause();
        }
        return false;
    }

    /**
     * Converts an exception or server error message into a user-friendly Chinese message.
     */
    static String toUserMessage(Throwable t) {
        if (t == null) return "未知错误";

        String msg = t.getMessage() != null ? t.getMessage() : "";
        Throwable cause = t.getCause();

        if (t instanceof ConnectException || (cause instanceof ConnectException)) {
            return "接收方不可达 (进程未启动或端口不通): " + (cause != null ? cause.getMessage() : msg);
        }
        if (t instanceof UnknownHostException || (cause instanceof UnknownHostException)) {
            return "无法解析接收方地址 (主机名无效或网络不可达): " + (cause != null ? cause.getMessage() : msg);
        }
        if (t instanceof SocketTimeoutException || (cause instanceof SocketTimeoutException)) {
            return "连接或读取超时 (接收方无响应或网络异常): " + (cause != null ? cause.getMessage() : msg);
        }
        if (t instanceof AccessDeniedException || (cause instanceof AccessDeniedException)) {
            return "本地文件无读取权限: " + (cause != null ? cause.getMessage() : msg);
        }
        if (t instanceof SecurityException || (cause instanceof SecurityException)) {
            return "本地文件无读取权限: " + (cause != null ? cause.getMessage() : msg);
        }

        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("permission denied") || lower.contains("access denied") || lower.contains("access is denied")
                || lower.contains("accessdenied") || lower.contains("拒绝访问") || lower.contains("权限")) {
            return "文件权限不足: " + msg;
        }
        if (lower.contains("no space left") || lower.contains("no space left on device")
                || lower.contains("disk full") || lower.contains("空间不足") || lower.contains("磁盘已满")) {
            return "接收方磁盘空间不足: " + msg;
        }

        return msg;
    }

    /**
     * Classifies server API error message (success: false, error: "...").
     */
    static String classifyServerError(String serverError) {
        if (serverError == null || serverError.isBlank()) return "服务端错误";

        String lower = serverError.toLowerCase(Locale.ROOT);
        if (lower.contains("no space") || lower.contains("disk full") || lower.contains("空间不足") || lower.contains("磁盘已满")) {
            return "接收方磁盘空间不足: " + serverError;
        }
        if (lower.contains("permission") || lower.contains("access denied") || lower.contains("access is denied")
                || lower.contains("拒绝访问") || lower.contains("权限")) {
            return "接收方文件无写权限: " + serverError;
        }
        return serverError;
    }

    /**
     * Checks local file readability and returns error message if not readable.
     */
    static String checkLocalFileReadable(String localFilePath) {
        if (localFilePath == null || localFilePath.isBlank()) return "本地路径为空";
        java.io.File f = new java.io.File(localFilePath);
        if (!f.exists()) return "本地文件不存在: " + localFilePath;
        if (!f.isFile()) return "本地路径不是文件: " + localFilePath;
        if (!f.canRead()) return "本地文件无读取权限: " + localFilePath;
        return null;
    }
}
