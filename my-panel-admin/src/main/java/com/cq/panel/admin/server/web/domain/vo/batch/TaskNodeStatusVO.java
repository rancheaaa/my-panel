package com.cq.panel.admin.server.web.domain.vo.batch;

import lombok.Data;

/**
 * 任务列表节点状态VO
 *
 * @author cq
 */
@Data
public class TaskNodeStatusVO {

    /** 节点ID */
    private String agentId;

    /** 节点名称 */
    private String agentName;

    /** 目录路径 */
    private String dirPath;

    /** 节点是否在线: 0-离线 1-在线 */
    private Integer nodeStatus;

    /** 目录是否存在: true-存在 false-不存在 */
    private Boolean dirExists;
}
