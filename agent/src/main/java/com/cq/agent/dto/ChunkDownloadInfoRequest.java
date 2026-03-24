package com.cq.agent.dto;

/**
 * Request for chunk download information.
 */
public class ChunkDownloadInfoRequest {

    private String traceId;
    private String transferId;
    private String remoteFilePath;
    private String sourceAgentId;
    private String sourceAgentIp;
    private int sourceAgentPort;
    private String destAgentId;
    private String destAgentIp;
    private int destAgentPort;

    public String getRemoteFilePath() {
        return remoteFilePath;
    }

    public void setRemoteFilePath(String remoteFilePath) {
        this.remoteFilePath = remoteFilePath;
    }

    public String getSourceAgentId() {
        return sourceAgentId;
    }

    public void setSourceAgentId(String sourceAgentId) {
        this.sourceAgentId = sourceAgentId;
    }

    public String getSourceAgentIp() {
        return sourceAgentIp;
    }

    public void setSourceAgentIp(String sourceAgentIp) {
        this.sourceAgentIp = sourceAgentIp;
    }

    public int getSourceAgentPort() {
        return sourceAgentPort;
    }

    public void setSourceAgentPort(int sourceAgentPort) {
        this.sourceAgentPort = sourceAgentPort;
    }

    public String getDestAgentId() {
        return destAgentId;
    }

    public void setDestAgentId(String destAgentId) {
        this.destAgentId = destAgentId;
    }

    public String getDestAgentIp() {
        return destAgentIp;
    }

    public void setDestAgentIp(String destAgentIp) {
        this.destAgentIp = destAgentIp;
    }

    public int getDestAgentPort() {
        return destAgentPort;
    }

    public void setDestAgentPort(int destAgentPort) {
        this.destAgentPort = destAgentPort;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}