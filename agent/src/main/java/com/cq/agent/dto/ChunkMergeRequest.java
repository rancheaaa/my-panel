package com.cq.agent.dto;

/**
 * Request body for chunk merge.
 */
public class ChunkMergeRequest {

    private String transferId;

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }
}
