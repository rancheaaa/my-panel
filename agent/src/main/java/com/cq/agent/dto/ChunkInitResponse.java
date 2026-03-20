package com.cq.agent.dto;

import java.util.List;

/**
 * Response data for chunk init API.
 */
public class ChunkInitResponse {

    private String transferId;
    private long totalSize;
    private int totalChunks;
    private int chunkSize;
    private List<Integer> missingChunks;
    private String initTime;

    public ChunkInitResponse() {
    }

    public ChunkInitResponse(String transferId, long totalSize, int totalChunks, int chunkSize, List<Integer> missingChunks) {
        this.transferId = transferId;
        this.totalSize = totalSize;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.missingChunks = missingChunks;
        this.initTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }

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

    public String getInitTime() {
        return initTime;
    }

    public void setInitTime(String initTime) {
        this.initTime = initTime;
    }

    @Override
    public String toString() {
        return "ChunkInitResponse{" +
                "transferId='" + transferId + '\'' +
                ", totalSize=" + totalSize +
                ", totalChunks=" + totalChunks +
                ", chunkSize=" + chunkSize +
                ", missingChunks=" + missingChunks +
                ", initTime='" + initTime + '\'' +
                '}';
    }
}