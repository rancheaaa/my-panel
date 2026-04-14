package com.cq.agent.dto;

import lombok.Data;

/**
 * Response for chunk download range.
 */
@Data
public class ChunkDownloadResponse {

    private boolean success;
    private String data;
    private String encoding;
    private long rangeStart;
    private long rangeEnd;
    private long totalSize;
    private String fileName;
}
