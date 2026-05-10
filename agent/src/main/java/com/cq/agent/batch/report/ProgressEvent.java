package com.cq.agent.batch.report;

/**
 * 进度事件
 */
public class ProgressEvent {
    private Long subtaskId;
    private int transferredBytes;
    private int totalBytes;
    private long timestamp;
    
    public ProgressEvent() {}
    
    public ProgressEvent(Long subtaskId, int transferredBytes, int totalBytes, long timestamp) {
        this.subtaskId = subtaskId;
        this.transferredBytes = transferredBytes;
        this.totalBytes = totalBytes;
        this.timestamp = timestamp;
    }
    
    public Long getSubtaskId() { return subtaskId; }
    public void setSubtaskId(Long subtaskId) { this.subtaskId = subtaskId; }
    
    public int getTransferredBytes() { return transferredBytes; }
    public void setTransferredBytes(int transferredBytes) { this.transferredBytes = transferredBytes; }
    
    public int getTotalBytes() { return totalBytes; }
    public void setTotalBytes(int totalBytes) { this.totalBytes = totalBytes; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    @Override
    public String toString() {
        return "ProgressEvent{subtask=" + subtaskId + 
               ", progress=" + transferredBytes + "/" + totalBytes +
               ", ts=" + timestamp + "}";
    }
}
