package com.cq.panel.common.dto.agent;

import lombok.Data;

/**
 * Request body for execute command API.
 */

@Data
public class AgentExecuteCommandRequest {

    private String command;
    private Long timeout;

}
