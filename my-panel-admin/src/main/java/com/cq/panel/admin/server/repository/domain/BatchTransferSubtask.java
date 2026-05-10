package com.cq.panel.admin.server.repository.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;

/**
 * 批量传输子任务对象 batch_transfer_subtask
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class BatchTransferSubtask implements java.io.Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 关联的任务ID(外键) */
    private Long taskId;

    /** 源文件绝对路径 */
    private String sourceFilePath;

    /** 目标Agent ID */
    private String targetAgentId;

    /** 目标目录绝对路径 */
    private String targetDir;

    /** 子任务状态: QUEUED/SENDING/COMPLETED/FAILED/RETRYING */
    private String status;

    /** 已传输分片数 */
    private Integer transferredChunks;

    /** 总分片数(-1表示未知) */
    private Integer totalChunks;

    /** 已传输字节数 */
    private Long transferredBytes;

    /** 总字节数(-1表示未知) */
    private Long totalBytes;

    /** 失败错误信息 */
    private String errorMessage;

    /** 当前重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextRetryAt;

    /** Agent端传输会话ID(用于断点续传) */
    private String transferId;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
