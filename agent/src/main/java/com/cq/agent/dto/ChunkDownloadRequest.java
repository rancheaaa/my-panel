package com.cq.agent.dto;

import lombok.Data;

/**
 * Request for chunk download.
 */
@Data
public class ChunkDownloadRequest {

    private String transferId;
    private String traceId;
    private int chunkIndex;
    private String destFileDir;
    private String destFileName;
}