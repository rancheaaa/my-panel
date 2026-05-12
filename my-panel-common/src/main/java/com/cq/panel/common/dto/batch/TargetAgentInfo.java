package com.cq.panel.common.dto.batch;

import java.io.Serial;
import java.io.Serializable;

/**
 * 目标Agent信息 (targetAgents数组元素)
 */
public class TargetAgentInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标Agent ID */
    private String agentId;

    /** 目标Agent名称 (ip:port格式) */
    private String agentName;

    /** 目标目录 */
    private String targetDir;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getTargetDir() {
        return targetDir;
    }

    public void setTargetDir(String targetDir) {
        this.targetDir = targetDir;
    }
}
