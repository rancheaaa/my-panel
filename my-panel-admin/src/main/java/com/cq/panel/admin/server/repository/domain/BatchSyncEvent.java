package com.cq.panel.admin.server.repository.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;

/**
 * 批量同步事件对象 batch_sync_event
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class BatchSyncEvent implements java.io.Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 事件类型: TASK_CREATED/TASK_UPDATED/TASK_DELETED/TASK_STATUS_CHANGED */
    private String eventType;

    /** 关联的任务ID */
    private Long taskId;

    /** 源Agent ID(用于路由) */
    private String sourceAgentId;

    /** 事件负载数据(JSON格式) */
    private String payload;

    /** 事件处理状态: PENDING/PROCESSING/COMPLETED/FAILED */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 错误信息 */
    private String errorMessage;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** 处理完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date processedAt;

    /** 过期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireAt;
}
