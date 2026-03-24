package com.cq.agent.dto;

import java.util.List;

/**
 * Response for chunk download information.
 */
public class ChunkDownloadInfoResponse {

    private String transferId;
    private long fileSize;
    private int totalChunks;
    private int chunkSize;
    private String fileName;
    private String initTime;

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getInitTime() {
        return initTime;
    }

    public void setInitTime(String initTime) {
        this.initTime = initTime;
    }

    @Override
    public String toString() {
        return "ChunkDownloadInfoResponse{" +
                "transferId='" + transferId + '\'' +
                ", fileSize=" + fileSize +
                ", totalChunks=" + totalChunks +
                ", chunkSize=" + chunkSize +
                ", fileName='" + fileName + '\'' +
                ", initTime='" + initTime + '\'' +
                '}';
    }
}