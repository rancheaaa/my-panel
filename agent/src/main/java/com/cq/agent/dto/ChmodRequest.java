package com.cq.agent.dto;

import lombok.Data;

/**
 * Request body for chmod.
 */
@Data
public class ChmodRequest {

    private String path;
    private String permissions;
}
