package com.cq.agent.client.download;

import com.cq.agent.client.TransferTask;
import com.cq.agent.client.upload.Util;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DownloadTask implements TransferTask<DownloadTaskStatus> {

    private String transferId;
    private String traceId;
    private String remoteFilePath;
    private String localFilePath;
    private String tmpLocalFilePath;
    private final String remoteAgentApiUrl;
    private final String remoteAgentUsername;
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
    private String exceptionDesc;

    public DownloadTask(String remoteFilePath, String localFilePath, long totalSize, String remoteAgentApiUrl, String remoteAgentUsername) {
        this.remoteFilePath = remoteFilePath;
        this.localFilePath = localFilePath;
        this.totalSize = totalSize;
        this.status = DownloadTaskStatus.PREPARED;
        this.createTime = Util.currentTime();
        this.updateTime = this.createTime;
        this.retryCount = -1;
        this.downloadedChunksCount = 0;
        this.remoteAgentApiUrl = remoteAgentApiUrl;
        this.remoteAgentUsername = remoteAgentUsername;
    }

    public void incrementDownloadChunksCount() {
        ++downloadedChunksCount;
    }

    public void incrementRetryCount() {
        ++retryCount;
    }

    public void updateTimestamp() {
        this.updateTime = Util.currentTime();
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
                ", exceptionDesc=" + exceptionDesc +
                ", remoteAgentApiUrl='" + remoteAgentApiUrl + '\'' +
                ", remoteAgentUsername='" + remoteAgentUsername + '\'' +
                '}';
    }
}