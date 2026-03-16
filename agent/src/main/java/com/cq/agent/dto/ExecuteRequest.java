package com.cq.agent.dto;

/**
 * Request body for execute command API.
 */
public class ExecuteRequest {

    private String command;
    private Long timeout;

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }
}
