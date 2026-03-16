package com.cq.agent.dto;

/**
 * Request body for chunk cancel.
 */
public class ChunkCancelRequest {

    private String transferId;

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }
}
