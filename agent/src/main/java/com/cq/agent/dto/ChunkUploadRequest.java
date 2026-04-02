package com.cq.agent.dto;

import lombok.Data;

/**
 * Request body for chunk upload (single chunk).
 */

@Data
public class ChunkUploadRequest {

    private String transferId;
    private int chunkIndex;
    private int chunkSize;
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
}