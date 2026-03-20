package com.cq.agent.client.upload;

/**
 * Status of an upload task.
 */
public enum UploadTaskStatus {
    PREPARED,
    SCANNED,
    INIT_UPLOADING,
    INIT_UPLOAD_COMPLETED,
    UPLOADING_CHUNKS,
    UPLOAD_CHUNKS_COMPLETED,
    MERGING_CHUNKS,
    MERGE_CHUNKS_COMPLETED,
    UPLOAD_SUCCESS,
    FAILED
}