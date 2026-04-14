package com.cq.agent.dto;

import lombok.Data;

/**
 * Response for execute command API.
 */

@Data
public class ExecuteResponse {

    private boolean success;
    private int exitCode;
    private String output;
    private String error;

}
