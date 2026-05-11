package com.cq.agent.client;

public interface TransferTask<STATUS> {
    void setStatus(STATUS status);
    STATUS getStatus();
    void updateTimestamp();
    String getTransferId();
}
