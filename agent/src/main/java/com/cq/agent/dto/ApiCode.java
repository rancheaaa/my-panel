package com.cq.agent.dto;

/**
 * Standard API status codes and default messages.
 */
public enum ApiCode {

    // General
    SUCCESS(200, "Success"),
    GENERIC_ERROR(500, "An unexpected error occurred"),
    NOT_FOUND(404, "Resource not found"),
    INVALID_REQUEST(400, "Invalid request format or parameters"),
    ACCESS_DENIED(403, "Access is denied"),
    // File Operations
    PATH_NOT_FOUND(1001, "Specified path does not exist"),
    PATH_IS_NOT_DIRECTORY(1002, "Specified path is not a directory"),
    PATH_IS_NOT_FILE(1003, "Specified path is not a file"),
    PATH_ALREADY_EXISTS(1004, "Specified path already exists"),
    FILE_NOT_READABLE(1005, "File is not readable"),
    FILE_TOO_LARGE(1006, "File size exceeds the maximum allowed limit"),
    DIRECTORY_NOT_EMPTY(1007, "Directory is not empty"),
    UNSUPPORTED_CHARSET(1008, "The specified charset is not supported"),
    UNSUPPORTED_ALGORITHM(1009, "The specified checksum algorithm is not supported"),
    CHMOD_NOT_SUPPORTED(1010, "CHMOD is not supported on the current operating system"),
    // Chunked Transfer
    UPLOAD_SESSION_NOT_FOUND(2001, "Upload session not found"),
    UPLOAD_SESSION_CONFLICT(2002, "Upload session conflicts with an existing one"),
    UPLOAD_NOT_COMPLETED(2003, "File upload is not yet complete"),
    CHUNKS_ALREADY_MERGED(2004, "Chunks for this upload have already been merged"),
    INVALID_CHUNK_INDEX(2005, "Invalid chunk index provided"),
    INVALID_CHUNK_SIZE(2006, "Invalid chunk size provided"),
    MERGE_SIZE_MISMATCH(2007, "Merged file size does not match the original file size"),
    INVALID_RANGE(2008, "Invalid download range specified"),
    // Command Execution
    COMMAND_NOT_FOUND(3001, "Command not found or is not executable"),
    COMMAND_EXECUTION_ERROR(3002, "Error executing command"),
    COMMAND_TIMED_OUT(3003, "Command execution timed out"),
    INTERNAL_SERVER_ERROR(500, "Internal server error");

    private final int code;
    private final String defaultMsg;

    ApiCode(int code, String defaultMsg) {
        this.code = code;
        this.defaultMsg = defaultMsg;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMsg() {
        return defaultMsg;
    }
}

