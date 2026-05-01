package com.cq.agent.client.upload;

import lombok.Data;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;

public class ErrorClassifier
{
    @Data
    public static class Classification
    {
        private ErrorType type;
        private boolean retryable;
        private String description;

        public Classification(ErrorType type, boolean retryable, String description)
        {
            this.type = type;
            this.retryable = retryable;
            this.description = description;
        }
    }

    public enum ErrorType
    {
        NETWORK_TIMEOUT(true),
        CONNECTION_RESET(true),
        TARGET_UNAVAILABLE(true),
        RATE_LIMIT_EXCEEDED(true),
        CHUNK_TRANSFER_ERROR(true),
        CHECKSUM_MISMATCH(false),
        DISK_SPACE_INSUFFICIENT(false),
        PERMISSION_DENIED(false),
        FILE_NOT_FOUND(false),
        TASK_CANCELLED(false),
        UNKNOWN(false);

        private final boolean defaultRetryable;

        ErrorType(boolean defaultRetryable)
        {
            this.defaultRetryable = defaultRetryable;
        }

        public boolean isDefaultRetryable()
        {
            return defaultRetryable;
        }
    }

    public static Classification classify(Throwable error)
    {
        if (error == null) return new Classification(ErrorType.UNKNOWN, false, "Unknown error");
        if (error instanceof SocketTimeoutException)
            return new Classification(ErrorType.NETWORK_TIMEOUT, true, "Network timeout");
        if (error instanceof ConnectException)
            return new Classification(ErrorType.CONNECTION_RESET, true, "Connection reset");
        if (error instanceof NoSuchFileException)
            return new Classification(ErrorType.FILE_NOT_FOUND, false, "File not found");
        if (error instanceof FileAlreadyExistsException)
            return new Classification(ErrorType.PERMISSION_DENIED, false, "File already exists");
        if (error instanceof SecurityException)
            return new Classification(ErrorType.PERMISSION_DENIED, false, "Permission denied");
        String msg = error.getMessage();
        if (msg != null)
        {
            String lower = msg.toLowerCase();
            if (lower.contains("timeout") || lower.contains("timed out"))
                return new Classification(ErrorType.NETWORK_TIMEOUT, true, "Timeout detected");
            if (lower.contains("connection") && (lower.contains("reset") || lower.contains("refused") || lower.contains("closed")))
                return new Classification(ErrorType.CONNECTION_RESET, true, "Connection error");
            if (lower.contains("rate limit") || lower.contains("too many"))
                return new Classification(ErrorType.RATE_LIMIT_EXCEEDED, true, "Rate limited");
            if (lower.contains("checksum") || lower.contains("md5") || lower.contains("mismatch"))
                return new Classification(ErrorType.CHECKSUM_MISMATCH, false, "Checksum mismatch");
            if (lower.contains("no space") || lower.contains("disk full"))
                return new Classification(ErrorType.DISK_SPACE_INSUFFICIENT, false, "Disk space insufficient");
            if (lower.contains("permission") || lower.contains("access denied") || lower.contains("forbidden"))
                return new Classification(ErrorType.PERMISSION_DENIED, false, "Permission denied");
            if (lower.contains("not found") || lower.contains("404"))
                return new Classification(ErrorType.FILE_NOT_FOUND, false, "Not found");
            if (lower.contains("cancel"))
                return new Classification(ErrorType.TASK_CANCELLED, false, "Task cancelled");
        }
        return new Classification(ErrorType.CHUNK_TRANSFER_ERROR, true, "Transfer error: " + msg);
    }
}
