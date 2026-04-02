package com.cq.agent.dto;

import lombok.Data;

/**
 * Request body for copy.
 */
@Data
public class CopyRequest {

    private String from;
    private String to;

}
