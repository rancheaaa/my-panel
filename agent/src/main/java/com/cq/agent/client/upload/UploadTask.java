package com.cq.agent.client.upload;

import com.cq.agent.client.TaskInfo;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory upload task. No persistence.
 */
@Getter
@Setter
public class UploadTask implements TaskInfo {
    private String localFilePath;
    private String remoteTargetPath;
    private String transferId;
    private String traceId;
    private UploadTaskStatus status;
    private String listenerClassName;
    private final String remoteAgentApiUrl;
    private final String remoteAgentUsername;

    /** 监听器状态恢复字段（用于重启恢复场景） */
    private Long taskId;
    private Long subtaskId;
    private Long scanBatchId;
    private Long fileBatchId;
    private String fileName;
    private long fileSize;

    private String createTime;
    private String updateTime;
    private String enqueuedTime;
    private String scannedStartTime;
    private String scannedEndTime;
    private String initUploadStartTime;
    private String initUploadEndTime;
    private String uploadChunksStartTime;
    private String uploadChunksEndTime;
    private String mergeChunksStartTime;
    private String mergeChunksEndTime;
    private String uploadSuccessTime;
    private String verifyStartTime;
    private String verifyEndTime;

    private int chunkSize;
    private int totalChunks;
    private final long totalSize;
    private String exceptionDesc;

    private List<Integer> missingChunks;
    private AtomicInteger uploadChunksCount = new AtomicInteger(0);
    private final AtomicInteger retryCount = new AtomicInteger(-1);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public UploadTask(String localFilePath, String remoteTargetPath, long totalSize, String remoteAgentApiUrl, String remoteAgentUsername) {
        this.localFilePath = localFilePath;
        this.remoteTargetPath = remoteTargetPath;
        this.totalSize = totalSize;
        this.status = UploadTaskStatus.PREPARED;
        this.createTime = FORMATTER.format(LocalDateTime.now());
        this.updateTime = this.createTime;
        this.remoteAgentApiUrl = remoteAgentApiUrl;
        this.remoteAgentUsername = remoteAgentUsername;
    }

    public void updateTimestamp() {
        this.updateTime = FORMATTER.format(java.time.LocalDateTime.now());
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

    public void setMissingChunks(List<Integer> missingChunks) {
        this.missingChunks = new ArrayList<>(missingChunks);
    }

    @Override
    public String toString() {
        return "UploadTask{" +
                "localFilePath='" + localFilePath + '\'' +
                ", remoteTargetPath='" + remoteTargetPath + '\'' +
                ", transferId='" + transferId + '\'' +
                ", traceId='" + traceId + '\'' +
                ", status=" + status +
                ", createTime='" + createTime + '\'' +
                ", updateTime='" + updateTime + '\'' +
                ", enqueuedTime='" + enqueuedTime + '\'' +
                ", scannedStartTime='" + scannedStartTime + '\'' +
                ", scannedEndTime='" + scannedEndTime + '\'' +
                ", initUploadStartTime='" + initUploadStartTime + '\'' +
                ", initUploadEndTime='" + initUploadEndTime + '\'' +
                ", uploadChunksStartTime='" + uploadChunksStartTime + '\'' +
                ", uploadChunksEndTime='" + uploadChunksEndTime + '\'' +
                ", mergeChunksStartTime='" + mergeChunksStartTime + '\'' +
                ", mergeChunksEndTime='" + mergeChunksEndTime + '\'' +
                ", uploadSuccessTime='" + uploadSuccessTime + '\'' +
                ", verifyStartTime='" + verifyStartTime + '\'' +
                ", verifyEndTime='" + verifyEndTime + '\'' +
                ", chunkSize=" + chunkSize +
                ", totalChunks=" + totalChunks +
                ", totalSize=" + totalSize +
                ", missingChunks=" + missingChunks +
                ", uploadChunksCount=" + uploadChunksCount +
                ", retryCount=" + retryCount +
                ", exceptionDesc=" + exceptionDesc +
                ", remoteAgentApiUrl='" + remoteAgentApiUrl + '\'' +
                ", remoteAgentUsername='" + remoteAgentUsername + '\'' +
                '}';
    }
}