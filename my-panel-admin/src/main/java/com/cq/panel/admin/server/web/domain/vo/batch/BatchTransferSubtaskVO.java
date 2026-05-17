package com.cq.panel.admin.server.web.domain.vo.batch;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 批量传输子任务VO
 */
@Data
public class BatchTransferSubtaskVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 关联的批量任务ID */
    private String taskId;

    /** 扫描批次ID(一次调度触发扫描到的N个文件共享) */
    private String scanBatchId;

    /** 文件批次ID(同一文件传输到多个Agent共享) */
    private String fileBatchId;

    /** 源Agent ID */
    private String sourceAgentId;

    /** 源节点名称，格式：user@ip:port */
    private String sourceAgentName;

    /** 目标Agent ID */
    private String targetAgentId;

    /** 目标节点名称，格式：user@ip:port */
    private String targetAgentName;

    /** 源文件完整路径(sourceDir+relativePath) */
    private String sourcePath;

    /** 目标文件完整路径(targetDir+relativePath) */
    private String targetPath;

    /** 文件名(纯文件名,不含路径) */
    private String fileName;

    /** 文件大小(字节) */
    private Long fileSizeBytes;

    /** 文件最后修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date fileLastModified;

    /** 子任务状态: QUEUED/SENDING/COMPLETED/FAILED/RETRYING */
    private String status;

    /** 底层分块传输会话ID(关联AgentUploader的transferId) */
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    /** 完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date completedAt;

    /** 传输耗时(毫秒) */
    private Long durationMs;

    /** 错误码 */
    private String errorCode;

    /** 错误详情 */
    private String errorMessage;

    /** 异常堆栈(调试用) */
    private String errorStackTrace;

    /** Agent本地重试次数 */
    private Integer retryCount;

    /** 最后一次重试时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastRetryAt;

    /** 下次可重试时间(Level 2) */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextRetryAfter;

    /** 创建人(系统自动) */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新人(系统自动) */
    private String updateBy;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /** 备注 */
    private String remark;
}
