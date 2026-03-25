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
    INVALID_PARAMETER(400, "Invalid parameter"),
    FILE_NOT_FOUND(1001, "File not found"),
    INVALID_FILE_SIZE(1002, "Invalid file size"),
    URI_NOT_SUPPORT(1003, "URI not supported"),

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
    DIRECTORY_NOT_WRITABLE(1011, "Directory is not writable"),
    DIRECTORY_CREATE_FAILED(1012, "Failed to create directory"),
    DIRECTORY_CREATE_NO_PERMISSION(1013, "No permission to create directory"),
    TEMP_DIR_CREATE_FAILED(1014, "Failed to create temporary directory"),
    TEMP_DIR_NO_WRITE_PERMISSION(1015, "No write permission in temporary directory"),
    TEMP_DIR_VERIFICATION_FAILED(1016, "Failed to verify temporary directory"),
    ACCESS_DENIED(1017, "Access denied"),
    RENAME_FAILED(1018, "Failed to rename file or directory"),
    COPY_FAILED(1019, "Failed to copy file or directory"),
    INSUFFICIENT_DISK_SPACE(1020, "Insufficient disk space for upload"),
    LIST_DIRECTORY_FAILED(1021, "Failed to list directory"),
    RETRIEVE_FILE_FAILED(1022, "Failed to retrieve file"),
    STORE_FILE_FAILED(1023, "Failed to store file"),
    APPEND_FILE_FAILED(1024, "Failed to append to file"),
    DELETE_FILE_FAILED(1025, "Failed to delete file"),
    CREATE_DIRECTORY_FAILED(1026, "Failed to create directory"),
    REMOVE_DIRECTORY_FAILED(1027, "Failed to remove directory"),
    GET_DIRECTORY_INFO_FAILED(1028, "Failed to get directory info"),
    GET_FILE_SIZE_FAILED(1029, "Failed to get file size"),
    GET_MODIFICATION_TIME_FAILED(1030, "Failed to get modification time"),
    SET_MODIFICATION_TIME_FAILED(1031, "Failed to set modification time"),
    GET_STATUS_FAILED(1032, "Failed to get status"),
    CHANGE_PERMISSIONS_FAILED(1033, "Failed to change permissions"),
    CALCULATE_CHECKSUM_FAILED(1034, "Failed to calculate checksum"),
    SEARCH_FAILED(1035, "Failed to search files"),
    GET_DISK_SPACE_FAILED(1036, "Failed to get disk space"),
    // Chunked Transfer
    UPLOAD_SESSION_NOT_FOUND(2001, "Upload session not found"),
    UPLOAD_SESSION_CONFLICT(2002, "Upload session conflicts with an existing one"),
    UPLOAD_NOT_COMPLETED(2003, "File upload is not yet complete"),
    CHUNKS_ALREADY_MERGED(2004, "Chunks for this upload have already been merged"),
    INVALID_CHUNK_INDEX(2005, "Invalid chunk index provided"),
    INVALID_CHUNK_SIZE(2006, "Invalid chunk size provided"),
    CHUNK_WRITE_SIZE_MISMATCH(2017, "Chunk write size verification failed"),
    MERGE_SIZE_MISMATCH(2007, "Merged file size does not match original file size"),
    INVALID_RANGE(2008, "Invalid download range specified"),
    INVALID_TOTAL_SIZE(2009, "Invalid total size provided"),
    FILE_SIZE_EXCEEDS_LIMIT(2010, "File size exceeds maximum allowed limit"),
    CHUNK_WRITE_FAILED(2011, "Failed to write chunk data"),
    INIT_UPLOAD_FAILED(2012, "Failed to initialize upload"),
    MERGE_CHUNKS_FAILED(2013, "Failed to merge chunks"),
    READ_FILE_FAILED(2014, "Failed to read file"),
    DOWNLOAD_DIRECTORY_FAILED(2015, "Cannot download directory"),
    GET_DOWNLOAD_INFO_FAILED(2016, "Failed to get download info"),
    CHUNK_NOT_RECEIVED(2018, "Chunk not yet received"),
    CHUNK_FILE_MISSING(2019, "Chunk file is missing"),
    CHUNK_FILE_SIZE_MISMATCH(2021, "Chunk file size mismatch"),
    CHUNK_FILE_CHECK_FAILED(2020, "Failed to check chunk file"),
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