package com.cq.agent.client.upload;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory upload task. No persistence.
 */
class UploadTask {
    private String localFilePath;
    private String remoteTargetPath;
    private String transferId;
    private String traceId;
    private UploadTaskStatus status;

    private String enqueuedTime;
    private String initUploadStartTime;
    private String initUploadEndTime;
    private String uploadChunksStartTime;
    private String uploadChunksEndTime;
    private String mergeChunksStartTime;
    private String mergeChunksEndTime;
    private String completedTime;
    private int chunkSize;
    private int totalChunks;
    private final AtomicInteger uploadChunksCount = new AtomicInteger(0);
    private final AtomicInteger retryCount = new AtomicInteger(-1);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public UploadTask(String localFilePath, String remoteTargetPath) {
        this.localFilePath = localFilePath;
        this.remoteTargetPath = remoteTargetPath;
        this.status = UploadTaskStatus.PREPARED;
    }

    // Getters and Setters
    public void setEnqueuedTime(String enqueuedTime) {
        this.enqueuedTime = enqueuedTime;
    }

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

    public String getEnqueuedTime() {
        return enqueuedTime;
    }

    public String getInitUploadStartTime() {
        return initUploadStartTime;
    }

    public void setInitUploadStartTime(String initUploadStartTime) {
        this.initUploadStartTime = initUploadStartTime;
    }

    public String getInitUploadEndTime() {
        return initUploadEndTime;
    }

    public void setInitUploadEndTime(String initUploadEndTime) {
        this.initUploadEndTime = initUploadEndTime;
    }

    public String getUploadChunksStartTime() {
        return uploadChunksStartTime;
    }

    public void setUploadChunksStartTime(String uploadChunksStartTime) {
        this.uploadChunksStartTime = uploadChunksStartTime;
    }

    public String getUploadChunksEndTime() {
        return uploadChunksEndTime;
    }

    public void setUploadChunksEndTime(String uploadChunksEndTime) {
        this.uploadChunksEndTime = uploadChunksEndTime;
    }

    public String getMergeChunksStartTime() {
        return mergeChunksStartTime;
    }

    public void setMergeChunksStartTime(String mergeChunksStartTime) {
        this.mergeChunksStartTime = mergeChunksStartTime;
    }

    public String getMergeChunksEndTime() {
        return mergeChunksEndTime;
    }

    public void setMergeChunksEndTime(String mergeChunksEndTime) {
        this.mergeChunksEndTime = mergeChunksEndTime;
    }

    public String getCompletedTime() {
        return completedTime;
    }

    public void setCompletedTime(String completedTime) {
        this.completedTime = completedTime;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getUploadChunksCount() {
        return uploadChunksCount.get();
    }

    public void incrementUploadChunksCount() {
        uploadChunksCount.incrementAndGet();
    }

    public int getRetryCount() {
        return retryCount.get();
    }

    public void incrementRetryCount() {
        retryCount.incrementAndGet();
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