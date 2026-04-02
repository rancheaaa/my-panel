package com.cq.agent.dto;

import lombok.Data;

import java.util.List;

/**
 * Response data for chunk init/status API.
 */

@Data
public class ChunkStatusResponse {

    private String transferId;
    private long totalSize;
    private int totalChunks;
    private int chunkSize;
    private List<Integer> missingChunks;
}
