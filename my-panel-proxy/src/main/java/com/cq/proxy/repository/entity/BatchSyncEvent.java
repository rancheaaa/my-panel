package com.cq.proxy.repository.entity;

import lombok.Data;

import java.util.Date;

/**
 * 批量同步事件实体 (Proxy端读取消费用)
 */
@Data
public class BatchSyncEvent {

    private Long id;

    /** 事件类型: TASK_CREATED/TASK_UPDATED/TASK_DELETED/TASK_STATUS_CHANGED */
    private String eventType;

    /** 关联的任务ID */
    private Long taskId;

    /** 源Agent ID */
    private String sourceAgentId;

    /** 事件负载(JSON格式, 存储完整的任务配置快照) */
    private String payload;

    /** 事件处理状态: PENDING/PROCESSING/COMPLETED/FAILED */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 失败原因 */
    private String errorMessage;

    /** 事件创建时间 */
    private Date createdAt;

    /** 事件处理完成时间 */
    private Date processedAt;

    /** 事件过期时间 */
    private Date expireAt;

    /** 事件开始处理时间 */
    private Date startedAt;

    /** 下次可重试时间 */
    private Date nextRetryAt;
}
