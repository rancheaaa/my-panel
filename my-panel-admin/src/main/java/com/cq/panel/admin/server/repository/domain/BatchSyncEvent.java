package com.cq.panel.admin.server.repository.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;

/**
 * 批量同步事件对象 batch_sync_event
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class BatchSyncEvent implements java.io.Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件ID */
    private Long id;

    /** 事件类型: TASK_CREATED/TASK_UPDATED/TASK_DELETED/TASK_STATUS_CHANGED */
    private String eventType;

    /** 关联的任务ID */
    private Long taskId;

    /** 源Agent ID(冗余存储,便于快速查询) */
    private String sourceAgentId;

    /** 事件负载(JSON格式, 存储完整的任务配置快照) */
    private String payload;

    /** 事件处理状态: PENDING-待处理/PROCESSING-处理中/COMPLETED-已完成/FAILED-失败 */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 失败原因 */
    private String errorMessage;

    /** 事件创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** 事件处理完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date processedAt;

    /** 事件过期时间(超过此时间未处理则标记为FAILED) */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireAt;
}
