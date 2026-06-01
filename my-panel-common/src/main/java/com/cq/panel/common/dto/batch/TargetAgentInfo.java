package com.cq.panel.common.dto.batch;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serial;
import java.io.Serializable;

/**
 * 目标Agent信息 (targetAgents数组元素)
 */
@Data
@NoArgsConstructor
public class TargetAgentInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标Agent ID */
    private String agentId;

    /** 目标Agent名称 (ip:port格式) */
    private String agentName;

    /** 目标目录 */
    private String targetDir;
}
