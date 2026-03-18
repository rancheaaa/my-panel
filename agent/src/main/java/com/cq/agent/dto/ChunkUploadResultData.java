package com.cq.agent.dto;

/**
 *
 * @author cq 2026/3/18 14:21
 * @since 1.0.0
 */
public class ChunkUploadResultData {

    private String transferId;
    private Boolean completed;
    private Integer missingChunksCount;

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public Integer getMissingChunksCount() {
        return missingChunksCount;
    }

    public void setMissingChunksCount(Integer missingChunksCount) {
        this.missingChunksCount = missingChunksCount;
    }
}
