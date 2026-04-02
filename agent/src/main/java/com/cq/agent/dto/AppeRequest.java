package com.cq.agent.dto;

import lombok.Data;

/**
 * Request body for append to file.
 */
@Data
public class AppeRequest {

    private String path;
    private String content;
    private String encoding;
}
