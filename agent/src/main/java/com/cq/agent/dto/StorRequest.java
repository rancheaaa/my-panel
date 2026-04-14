package com.cq.agent.dto;

import lombok.Data;

/**
 * Request body for store file (path + content).
 */

@Data
public class StorRequest {

    private String path;
    private String content;
    private String encoding;

}
