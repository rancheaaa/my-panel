package com.cq.agent.dto;

/**
 * Request body for chunk upload (single chunk).
 */
public class ChunkUploadRequest {

    private String transferId;
    private int chunkIndex;
    private String content;
    private String encoding;

    // Source agent info
    private String sourceAgentId;
    private String sourceAgentIp;
    private int sourceAgentPort;
    private String sourceFileDir;
    private String sourceFileName;

    // Destination agent info
    private String destAgentId;
    private String destAgentIp;
    private int destAgentPort;
    private String destFileDir;
    private String destFileName;

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
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

    public String getSourceFileDir() {
        return sourceFileDir;
    }

    public void setSourceFileDir(String sourceFileDir) {
        this.sourceFileDir = sourceFileDir;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
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

    public String getDestFileDir() {
        return destFileDir;
    }

    public void setDestFileDir(String destFileDir) {
        this.destFileDir = destFileDir;
    }

    public String getDestFileName() {
        return destFileName;
    }

    public void setDestFileName(String destFileName) {
        this.destFileName = destFileName;
    }

    @Override
    public String toString() {
        return "ChunkUploadRequest{" +
                "transferId='" + transferId + '\'' +
                ", chunkIndex=" + chunkIndex +
                ", content='" + content + '\'' +
                ", encoding='" + encoding + '\'' +
                ", sourceAgentId='" + sourceAgentId + '\'' +
                ", sourceAgentIp='" + sourceAgentIp + '\'' +
                ", sourceAgentPort=" + sourceAgentPort +
                ", sourceFileDir='" + sourceFileDir + '\'' +
                ", sourceFileName='" + sourceFileName + '\'' +
                ", destAgentId='" + destAgentId + '\'' +
                ", destAgentIp='" + destAgentIp + '\'' +
                ", destAgentPort=" + destAgentPort +
                ", destFileDir='" + destFileDir + '\'' +
                ", destFileName='" + destFileName + '\'' +
                '}';
    }
}
