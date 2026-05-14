package com.cq.agent.batch.report;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Date;

/**
 * 子任务事件（扩展版）
 * 包含batch_transfer_subtask表的完整字段信息
 * 用于Agent向Proxy上报文件传输明细
 */
@Data
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class SubTaskEvent {

    private Long subtaskId;
    private Long taskId;
    private String transferId;

    /** Agent信息 */
    private String sourceAgentId;
    private String sourceAgentName;
    private String targetAgentId;
    private String targetAgentName;

    /** 文件信息 */
    private String sourcePath;
    private String targetPath;
    private String fileName;
    private Long fileSizeBytes;
    private Date fileLastModified;

    /** 传输状态 */
    private String status;

    /** 进度信息 */
    private Integer transferredChunks;
    private Integer totalChunks;
    private Long transferredBytes;
    private Long totalBytes;
    private Long speedBytesPerSec;

    /** 时间信息 */
    private Date startedAt;
    private Date completedAt;
    private Long durationMs;

    /** 错误信息 */
    private String errorCode;
    private String errorMessage;
    private String errorStackTrace;

    /** 重试信息 */
    private Integer retryCount;
    private Date lastRetryAt;
    private Date nextRetryAfter;
}
