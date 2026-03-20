package com.cq.agent.client.upload;

import com.cq.agent.dto.ApiCode;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.AccessDeniedException;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

/**
 * Classifies upload errors into user-friendly messages for:
 * - Receiver unreachable (process down, port closed)
 * - Disk full on receiver
 * - Permission denied (local read / remote write)
 */
final class UploadErrorClassifier {

    private UploadErrorClassifier() {}

    private static final Set<Integer> RETRYABLE_CODES = new HashSet<>();
    static {
        RETRYABLE_CODES.add(ApiCode.GENERIC_ERROR.getCode());
        RETRYABLE_CODES.add(ApiCode.INIT_UPLOAD_FAILED.getCode());
        RETRYABLE_CODES.add(ApiCode.CHUNK_WRITE_FAILED.getCode());
        RETRYABLE_CODES.add(ApiCode.MERGE_CHUNKS_FAILED.getCode());
        RETRYABLE_CODES.add(ApiCode.READ_FILE_FAILED.getCode());
        RETRYABLE_CODES.add(ApiCode.GET_DISK_SPACE_FAILED.getCode());
    }

    private static final Set<Integer> NON_RETRYABLE_CODES = new HashSet<>();
    static {
        NON_RETRYABLE_CODES.add(ApiCode.NOT_FOUND.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.INVALID_REQUEST.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.PATH_NOT_FOUND.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.PATH_IS_NOT_DIRECTORY.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.PATH_IS_NOT_FILE.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.PATH_ALREADY_EXISTS.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.FILE_NOT_READABLE.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.DIRECTORY_NOT_EMPTY.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.DIRECTORY_NOT_WRITABLE.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.DIRECTORY_CREATE_NO_PERMISSION.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.ACCESS_DENIED.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.RENAME_FAILED.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.COPY_FAILED.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.DELETE_FILE_FAILED.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.INSUFFICIENT_DISK_SPACE.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.INVALID_TOTAL_SIZE.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.FILE_SIZE_EXCEEDS_LIMIT.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.INVALID_CHUNK_INDEX.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.INVALID_CHUNK_SIZE.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.CHUNK_WRITE_SIZE_MISMATCH.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.MERGE_SIZE_MISMATCH.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.INVALID_RANGE.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.UPLOAD_SESSION_NOT_FOUND.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.UPLOAD_SESSION_CONFLICT.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.UPLOAD_NOT_COMPLETED.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.CHUNKS_ALREADY_MERGED.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.TEMP_DIR_CREATE_FAILED.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.TEMP_DIR_NO_WRITE_PERMISSION.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.TEMP_DIR_VERIFICATION_FAILED.getCode());
        NON_RETRYABLE_CODES.add(ApiCode.DOWNLOAD_DIRECTORY_FAILED.getCode());
    }

    static boolean isRetryableByCode(int errorCode) {
        if (NON_RETRYABLE_CODES.contains(errorCode)) {
            return false;
        }
        return RETRYABLE_CODES.contains(errorCode) || errorCode >= 500;
    }

    /**
     * if (t == null) return "未知错误";
     *
     *         String msg = t.getMessage() != null ? t.getMessage() : "";
     *         Throwable cause = t.getCause();
     *
     *         if (t instanceof ConnectException || (cause instanceof ConnectException)) {
     *             return "接收方不可达 (进程未启动或端口不通): " + (cause != null ? cause.getMessage() : msg);
     *         }
     *         if (t instanceof UnknownHostException || (cause instanceof UnknownHostException)) {
     *             return "无法解析接收方地址 (主机名无效或网络不可达): " + (cause != null ? cause.getMessage() : msg);
     *         }
     *         if (t instanceof SocketTimeoutException || (cause instanceof SocketTimeoutException)) {
     *             return "连接或读取超时 (接收方无响应或网络异常): " + (cause != null ? cause.getMessage() : msg);
     *         }
     *         if (t instanceof AccessDeniedException || (cause instanceof AccessDeniedException)) {
     *             return "本地文件无读取权限: " + (cause != null ? cause.getMessage() : msg);
     *         }
     *         if (t instanceof SecurityException || (cause instanceof SecurityException)) {
     *             return "本地文件无读取权限: " + (cause != null ? cause.getMessage() : msg);
     *         }
     *
     *         String lower = msg.toLowerCase(Locale.ROOT);
     *         if (lower.contains("permission denied") || lower.contains("access denied") || lower.contains("access is denied")
     *                 || lower.contains("accessdenied") || lower.contains("拒绝访问") || lower.contains("权限")) {
     *             return "文件权限不足: " + msg;
     *         }
     *         if (lower.contains("no space left") || lower.contains("no space left on device")
     *                 || lower.contains("disk full") || lower.contains("空间不足") || lower.contains("磁盘已满")) {
     *             return "接收方磁盘空间不足: " + msg;
     *         }
     */
}