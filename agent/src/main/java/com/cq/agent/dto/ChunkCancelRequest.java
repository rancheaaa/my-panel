package com.cq.agent.dto;

import lombok.Data;

/**
 * Request body for chunk cancel.
 */
@Data
public class ChunkCancelRequest {

    private String transferId;

}
