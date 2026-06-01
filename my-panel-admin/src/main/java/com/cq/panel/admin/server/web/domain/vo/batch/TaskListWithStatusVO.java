package com.cq.panel.admin.server.web.domain.vo.batch;

import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import lombok.Data;

import java.util.List;

/**
 * 任务列表带节点状态VO
 * 
 * @author cq
 */
@Data
public class TaskListWithStatusVO {

    /** 任务信息 */
    private BatchTransferTask task;

    /** 源节点状态 */
    private TaskNodeStatusVO sourceNodeStatus;

    /** 目标节点状态列表 */
    private List<TaskNodeStatusVO> targetNodeStatusList;
}
