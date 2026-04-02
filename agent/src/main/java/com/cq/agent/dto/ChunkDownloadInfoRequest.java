package com.cq.agent.dto;

import lombok.Data;

/**
 * Request for chunk download information.
 */
@Data
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
}