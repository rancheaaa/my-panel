package com.cq.agent.dto;

/**
 * Request body for set modification time.
 */
public class MfmtRequest {

    private String path;
    private long timestamp;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
