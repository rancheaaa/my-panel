package com.cq.agent.dto;

import lombok.Data;

/**
 * Response for chunk download information.
 */
@Data
public class ChunkDownloadInfoResponse {

    private String transferId;
    private long fileSize;
    private int totalChunks;
    private int chunkSize;
    private String fileName;
    private String initTime;
}