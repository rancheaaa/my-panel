package com.cq.agent.dto;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author cq 2026/3/18 14:21
 * @since 1.0.0
 */
public class ChunkUploadResponse {

    private String transferId;
    private Boolean completed;
    private AtomicInteger missingChunksCount;

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
        return missingChunksCount.get();
    }

    public void setMissingChunksCount(Integer missingChunksCount) {
        this.missingChunksCount.set(missingChunksCount);
    }
}
