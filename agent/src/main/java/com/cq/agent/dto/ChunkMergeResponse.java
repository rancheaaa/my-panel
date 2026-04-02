package com.cq.agent.dto;

import lombok.Data;

/**
 * Response data for chunk merge API.
 */

@Data
public class ChunkMergeResponse {

    private String destFileDir;
    private String destFileName;
    private long size;
    private String checksum;
}
