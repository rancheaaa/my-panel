package com.cq.agent.client.upload;

import com.cq.agent.dto.ChunkInitResponse;
import com.cq.agent.dto.ChunkStatusResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the state of a chunked upload session.
 * This object is populated from the server's responses.
 */
class UploadState {

    private final String transferId;
    private final long totalSize;
    private final int totalChunks;
    private final int chunkSize;
    private final Set<Integer> missingChunks;

    UploadState(String transferId, long totalSize, int totalChunks, int chunkSize, List<Integer> missingChunks) {
        this.transferId = transferId;
        this.totalSize = totalSize;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.missingChunks = ConcurrentHashMap.newKeySet();
        if (missingChunks != null) {
            this.missingChunks.addAll(missingChunks);
        }
    }

    UploadState(ChunkInitResponse response) {
        this(response.getTransferId(), response.getTotalSize(), response.getTotalChunks(), response.getChunkSize(), response.getMissingChunks());
    }

    UploadState(ChunkStatusResponse data) {
        this(data.getTransferId(), data.getTotalSize(), data.getTotalChunks(), data.getChunkSize(), data.getMissingChunks());
    }

    public String getTransferId() {
        return transferId;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public Set<Integer> getMissingChunks() {
        return missingChunks;
    }

    @Override
    public String toString() {
        return "UploadState{" +
                "transferId='" + transferId + '\'' +
                ", totalSize=" + totalSize +
                ", totalChunks=" + totalChunks +
                ", chunkSize=" + chunkSize +
                ", missingChunks=" + missingChunks +
                '}';
    }
}