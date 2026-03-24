package com.cq.agent.client.download;

import com.cq.agent.client.upload.Util;

public class DownloadTask {

    private String transferId;
    private String traceId;
    private String remoteFilePath;
    private String localFilePath;
    private String tmpLocalFilePath;
    private long totalSize;
    private DownloadTaskStatus status;
    private String createTime;
    private String updateTime;
    private String enqueuedTime;
    private String initDownloadStartTime;
    private String initDownloadEndTime;
    private String downloadChunksStartTime;
    private String downloadChunksEndTime;
    private String mergeChunksStartTime;
    private String mergeChunksEndTime;
    private String verifyStartTime;
    private String verifyEndTime;
    private String downloadSuccessTime;
    private int chunkSize;
    private int totalChunks;
    private int downloadedChunksCount;
    private int retryCount;
    private String listenerClassName;

    public DownloadTask(String remoteFilePath, String localFilePath, long totalSize) {
        this.remoteFilePath = remoteFilePath;
        this.localFilePath = localFilePath;
        this.totalSize = totalSize;
        this.status = DownloadTaskStatus.PREPARED;
        this.createTime = Util.currentTime();
        this.updateTime = this.createTime;
        this.retryCount = -1;
        this.downloadedChunksCount = 0;
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

    public String getRemoteFilePath() {
        return remoteFilePath;
    }

    public void setRemoteFilePath(String remoteFilePath) {
        this.remoteFilePath = remoteFilePath;
    }

    public String getLocalFilePath() {
        return localFilePath;
    }

    public void setLocalFilePath(String localFilePath) {
        this.localFilePath = localFilePath;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public DownloadTaskStatus getStatus() {
        return status;
    }

    public void setStatus(DownloadTaskStatus status) {
        this.status = status;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public String getEnqueuedTime() {
        return enqueuedTime;
    }

    public void setEnqueuedTime(String enqueuedTime) {
        this.enqueuedTime = enqueuedTime;
    }

    public String getInitDownloadStartTime() {
        return initDownloadStartTime;
    }

    public void setInitDownloadStartTime(String initDownloadStartTime) {
        this.initDownloadStartTime = initDownloadStartTime;
    }

    public String getInitDownloadEndTime() {
        return initDownloadEndTime;
    }

    public void setInitDownloadEndTime(String initDownloadEndTime) {
        this.initDownloadEndTime = initDownloadEndTime;
    }

    public String getDownloadChunksStartTime() {
        return downloadChunksStartTime;
    }

    public void setDownloadChunksStartTime(String downloadChunksStartTime) {
        this.downloadChunksStartTime = downloadChunksStartTime;
    }

    public String getDownloadChunksEndTime() {
        return downloadChunksEndTime;
    }

    public void setDownloadChunksEndTime(String downloadChunksEndTime) {
        this.downloadChunksEndTime = downloadChunksEndTime;
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

    public String getVerifyStartTime() {
        return verifyStartTime;
    }

    public void setVerifyStartTime(String verifyStartTime) {
        this.verifyStartTime = verifyStartTime;
    }

    public String getVerifyEndTime() {
        return verifyEndTime;
    }

    public void setVerifyEndTime(String verifyEndTime) {
        this.verifyEndTime = verifyEndTime;
    }

    public String getDownloadSuccessTime() {
        return downloadSuccessTime;
    }

    public void setDownloadSuccessTime(String downloadSuccessTime) {
        this.downloadSuccessTime = downloadSuccessTime;
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

    public int getDownloadedChunksCount() {
        return downloadedChunksCount;
    }

    public void setDownloadedChunksCount(int downloadedChunksCount) {
        this.downloadedChunksCount = downloadedChunksCount;
    }

    public int incrementDownloadChunksCount() {
        return ++downloadedChunksCount;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getListenerClassName() {
        return listenerClassName;
    }

    public void setListenerClassName(String listenerClassName) {
        this.listenerClassName = listenerClassName;
    }

    public int incrementRetryCount() {
        return ++retryCount;
    }

    public void updateTimestamp() {
        this.updateTime = Util.currentTime();
    }

    public String getTmpLocalFilePath() {
        return tmpLocalFilePath;
    }

    public void setTmpLocalFilePath(String tmpLocalFilePath) {
        this.tmpLocalFilePath = tmpLocalFilePath;
    }

    @Override
    public String toString() {
        return "DownloadTask{" +
                "remoteFilePath='" + remoteFilePath + '\'' +
                ", localFilePath='" + localFilePath + '\'' +
                ", transferId='" + transferId + '\'' +
                ", traceId='" + traceId + '\'' +
                ", status=" + status +
                ", createTime='" + createTime + '\'' +
                ", updateTime='" + updateTime + '\'' +
                ", enqueuedTime='" + enqueuedTime + '\'' +
                ", initDownloadStartTime='" + initDownloadStartTime + '\'' +
                ", initDownloadEndTime='" + initDownloadEndTime + '\'' +
                ", downloadChunksStartTime='" + downloadChunksStartTime + '\'' +
                ", downloadChunksEndTime='" + downloadChunksEndTime + '\'' +
                ", mergeChunksStartTime='" + mergeChunksStartTime + '\'' +
                ", mergeChunksEndTime='" + mergeChunksEndTime + '\'' +
                ", verifyStartTime='" + verifyStartTime + '\'' +
                ", verifyEndTime='" + verifyEndTime + '\'' +
                ", downloadSuccessTime='" + downloadSuccessTime + '\'' +
                ", chunkSize=" + chunkSize +
                ", totalChunks=" + totalChunks +
                ", totalSize=" + totalSize +
                ", downloadedChunksCount=" + downloadedChunksCount +
                ", retryCount=" + retryCount +
                ", tmpLocalFilePath=" + tmpLocalFilePath +
                '}';
    }
}