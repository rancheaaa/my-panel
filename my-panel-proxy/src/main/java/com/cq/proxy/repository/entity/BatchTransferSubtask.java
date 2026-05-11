package com.cq.proxy.repository.entity;

import lombok.Data;

import java.util.Date;

/**
 * 批量传输子任务实体 (Proxy端更新进度用)
 * 对应表 batch_transfer_subtask
 */
@Data
public class BatchTransferSubtask {

    private Long id;

    /** 关联的批量任务ID */
    private Long taskId;

    /** 源Agent ID */
    private String sourceAgentId;

    /** 源节点名称 */
    private String sourceAgentName;

    /** 目标Agent ID */
    private String targetAgentId;

    /** 目标节点名称 */
    private String targetAgentName;

    /** 源文件完整路径 */
    private String sourcePath;

    /** 目标文件完整路径 */
    private String targetPath;

    /** 文件名 */
    private String fileName;

    /** 文件大小(字节) */
    private Long fileSizeBytes;

    /** 文件最后修改时间 */
    private Date fileLastModified;

    /** 子任务状态: QUEUED/SENDING/COMPLETED/FAILED/RETRYING */
    private String status;

    /** 底层分块传输会话ID */
    private String transferId;

    /** 已传输的分块数 */
    private Integer transferredChunks;

    /** 总分块数 */
    private Integer totalChunks;

    /** 已传输字节数 */
    private Long transferredBytes;

    /** 当前传输速率(字节/秒) */
    private Long speedBytesPerSec;

    /** 开始传输时间 */
    private Date startedAt;

    /** 完成时间 */
    private Date completedAt;

    /** 传输耗时(毫秒) */
    private Long durationMs;

    /** 错误码 */
    private String errorCode;

    /** 错误详情 */
    private String errorMessage;

    /** 异常堆栈 */
    private String errorStackTrace;

    /** Agent本地重试次数 */
    private Integer retryCount;

    /** 最后一次重试时间 */
    private Date lastRetryAt;

    /** 下次可重试时间 */
    private Date nextRetryAfter;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private Date createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private Date updateTime;

    /** 备注 */
    private String remark;
}
