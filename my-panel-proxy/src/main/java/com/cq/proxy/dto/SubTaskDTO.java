package com.cq.proxy.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 子任务上报DTO
 * 用于接收Agent上报的子任务创建、状态更新、进度更新等请求
 * 替代原来的Map<String, Object>，提供类型安全
 */
@Data
public class SubTaskDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 子任务ID（数据库主键，创建后由Proxy返回） */
    private Long id;

    /** 子任务ID（Agent端生成） */
    private Long subtaskId;

    /** 主任务ID */
    private Long taskId;

    /** 扫描批次ID(一次调度触发扫描到的N个文件共享) */
    private Long scanBatchId;

    /** 文件批次ID(同一文件传输到多个Agent共享) */
    private Long fileBatchId;

    /** 传输ID（UUID） */
    private String transferId;

    // ==================== Agent信息 ====================

    /** 源Agent ID */
    private String sourceAgentId;

    /** 源Agent名称 (ip:port格式) */
    private String sourceAgentName;

    /** 目标Agent ID */
    private String targetAgentId;

    /** 目标Agent名称 (ip:port格式) */
    private String targetAgentName;

    // ==================== 文件信息 ====================

    /** 源文件绝对路径 */
    private String sourcePath;

    /** 目标文件路径 */
    private String targetPath;

    /** 文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSizeBytes;

    /**
     * 文件最后修改时间（格式: yyyy-MM-dd HH:mm:ss）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date fileLastModified;

    // ==================== 传输状态 ====================

    /**
     * 状态: QUEUED, SENDING, COMPLETED, FAILED, RETRYING
     */
    private String status;

    // ==================== 进度信息 ====================

    /** 已传输分块数 */
    private Integer transferredChunks;

    /** 总分块数 */
    private Integer totalChunks;

    /** 已传输字节数 */
    private Long transferredBytes;

    /** 传输速度（字节/秒） */
    private Long speedBytesPerSec;

    // ==================== 时间信息 ====================

    /**
     * 开始时间（格式: yyyy-MM-dd HH:mm:ss）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startedAt;

    /**
     * 完成时间（格式: yyyy-MM-dd HH:mm:ss）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date completedAt;

    /** 耗时（毫秒） */
    private Long durationMs;

    // ==================== 错误信息 ====================

    /** 错误码 */
    private String errorCode;

    /** 错误消息 */
    private String errorMessage;

    /** 错误堆栈 */
    private String errorStackTrace;

    // ==================== 重试信息 ====================

    /** 重试次数 */
    private Integer retryCount;

    /**
     * 上次重试时间（格式: yyyy-MM-dd HH:mm:ss）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastRetryAt;

    /**
     * 下次重试时间（格式: yyyy-MM-dd HH:mm:ss）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date nextRetryAfter;

    // ==================== 进度专用字段（用于/progress接口）====================

    /** 时间戳（用于过期数据检测） */
    private Long timestamp;

    /** 总字节数（用于进度计算） */
    private Integer totalBytes;

    /** 序列号 */
    private Integer sequenceNumber;
}
