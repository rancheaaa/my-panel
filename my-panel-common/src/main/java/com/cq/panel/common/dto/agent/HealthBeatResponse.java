package com.cq.panel.common.dto.agent;

import lombok.Data;

/**
 * Response for health check API.
 */

@Data
public class HealthBeatResponse {

    private String status;
    private String os;
    private String osType;
    private long defaultTimeout;
    private long maxTimeout;

}
