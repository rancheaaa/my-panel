package com.cq.agent.dto;

import lombok.Data;

/**
 * Response for health check API.
 */

@Data
public class HealthResponse {

    private String status;
    private String os;
    private String osType;
    private long defaultTimeout;
    private long maxTimeout;

}
