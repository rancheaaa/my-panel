package com.cq.agent.client.upload;

import java.util.Objects;

/**
 * In-memory upload task. No persistence.
 */
class UploadTask {
    private String localFilePath;
    private String remoteTargetPath;
    private String transferId;
    private String traceId;
    private UploadTaskStatus status;

    public UploadTask(String localFilePath, String remoteTargetPath) {
        this.localFilePath = localFilePath;
        this.remoteTargetPath = remoteTargetPath;
        this.status = UploadTaskStatus.PENDING;
    }

    // Getters and Setters

    public String getLocalFilePath() {
        return localFilePath;
    }

    public void setLocalFilePath(String localFilePath) {
        this.localFilePath = localFilePath;
    }

    public String getRemoteTargetPath() {
        return remoteTargetPath;
    }

    public void setRemoteTargetPath(String remoteTargetPath) {
        this.remoteTargetPath = remoteTargetPath;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public UploadTaskStatus getStatus() {
        return status;
    }

    public void setStatus(UploadTaskStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UploadTask that = (UploadTask) o;
        return Objects.equals(localFilePath, that.localFilePath) &&
               Objects.equals(remoteTargetPath, that.remoteTargetPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localFilePath, remoteTargetPath);
    }
}