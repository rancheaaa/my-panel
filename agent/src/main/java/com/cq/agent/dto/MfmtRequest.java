package com.cq.agent.dto;

import lombok.Data;

/**
 * Request body for set modification time.
 */

@Data
public class MfmtRequest {

    private String path;
    private long timestamp;

}
