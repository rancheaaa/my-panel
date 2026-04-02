package com.cq.agent.dto;

import lombok.Data;

/**
 * Request body for chunk upload init.
 */
@Data
public class ChunkInitRequest {

    private String transferId;
    private long totalSize;

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
