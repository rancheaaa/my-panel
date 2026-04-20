package com.cq.panel.common.dto.agent;

import lombok.Data;

/**
 * Response for execute command API.
 */

@Data
public class AgentExecuteCommandResponse {

    private boolean success;
    private int exitCode;
    private String output;
    private String error;

}
