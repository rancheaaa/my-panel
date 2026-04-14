package com.cq.panel.admin.server.web.domain.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Agent执行命令DTO
 * 
 * @author cq
 */
@Schema(description = "Agent执行命令参数")
public class AgentExecuteCommandDTO {

    @Schema(description = "Agent节点ID", required = true)
    @NotBlank(message = "节点ID不能为空")
    private String agentId;

    @Schema(description = "要执行的命令", required = true)
    @NotBlank(message = "命令不能为空")
    private String command;

    @Schema(description = "执行超时时间（秒）", defaultValue = "30")
    @NotNull(message = "超时时间不能为空")
    @Positive(message = "超时时间必须大于0")
    private Integer timeout = 30;

    // Getters and Setters
    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }
}