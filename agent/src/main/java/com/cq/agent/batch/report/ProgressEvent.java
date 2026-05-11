package com.cq.agent.batch.report;

/**
 * 进度事件
 * 符合spec.md设计要求的数据结构
 */
public class ProgressEvent {
    private Long subtaskId;
    private Long taskId;
    private String transferId;
    private String status;
    private int transferredChunks;
    private int totalChunks;
    private long transferredBytes;
    private long speedBytesPerSec;
    private long timestamp;
    private int sequenceNumber;

    /** 兼容旧版本的字段 */
    private int totalBytes;

    public ProgressEvent() {}

    /**
     * 旧版本构造函数（向后兼容）
     */
    public ProgressEvent(Long subtaskId, int transferredBytes, int totalBytes, long timestamp) {
        this.subtaskId = subtaskId;
        this.transferredBytes = transferredBytes;
        this.totalBytes = totalBytes;
        this.timestamp = timestamp;
    }

    /**
     * 完整构造函数（符合spec.md设计要求）
     */
    public ProgressEvent(Long subtaskId, Long taskId, String transferId, String status,
                         int transferredChunks, int totalChunks, long transferredBytes,
                         long speedBytesPerSec, long timestamp, int sequenceNumber) {
        this.subtaskId = subtaskId;
        this.taskId = taskId;
        this.transferId = transferId;
        this.status = status;
        this.transferredChunks = transferredChunks;
        this.totalChunks = totalChunks;
        this.transferredBytes = transferredBytes;
        this.speedBytesPerSec = speedBytesPerSec;
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
    }

    public Long getSubtaskId() { return subtaskId; }
    public void setSubtaskId(Long subtaskId) { this.subtaskId = subtaskId; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getTransferId() { return transferId; }
    public void setTransferId(String transferId) { this.transferId = transferId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTransferredChunks() { return transferredChunks; }
    public void setTransferredChunks(int transferredChunks) { this.transferredChunks = transferredChunks; }

    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

    public long getTransferredBytes() { return transferredBytes; }
    public void setTransferredBytes(long transferredBytes) { this.transferredBytes = transferredBytes; }

    public long getSpeedBytesPerSec() { return speedBytesPerSec; }
    public void setSpeedBytesPerSec(long speedBytesPerSec) { this.speedBytesPerSec = speedBytesPerSec; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public int getTotalBytes() { return totalBytes; }
    public void setTotalBytes(int totalBytes) { this.totalBytes = totalBytes; }

    @Override
    public String toString() {
        return "ProgressEvent{subtask=" + subtaskId +
               ", task=" + taskId +
               ", transferId=" + transferId +
               ", status=" + status +
               ", chunks=" + transferredChunks + "/" + totalChunks +
               ", bytes=" + transferredBytes +
               ", speed=" + speedBytesPerSec +
               ", seq=" + sequenceNumber +
               ", ts=" + timestamp + "}";
    }
}
