package com.cq.agent.dto;

import lombok.Data;

/**
 * Request body for execute command API.
 */

@Data
public class ExecuteRequest {

    private String command;
    private Long timeout;

}
