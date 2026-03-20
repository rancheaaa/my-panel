package com.cq.agent.dto;

import java.util.List;

/**
 * Response data for chunk init/status API.
 */
public class ChunkStatusResponse {

    private String transferId;
    private long totalSize;
    private int totalChunks;
    private int chunkSize;
    private List<Integer> missingChunks;

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public List<Integer> getMissingChunks() {
        return missingChunks;
    }

    public void setMissingChunks(List<Integer> missingChunks) {
        this.missingChunks = missingChunks;
    }
}
