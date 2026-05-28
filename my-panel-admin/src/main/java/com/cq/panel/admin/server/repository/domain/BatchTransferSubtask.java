package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;

/**
 * 批量传输子任务对象 batch_transfer_subtask
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class BatchTransferSubtask implements java.io.Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @Excel(name = "子任务ID", type = Excel.Type.EXPORT, cellType = Excel.ColumnType.NUMERIC)
    private Long id;

    /** 关联的批量任务ID */
    @Excel(name = "任务ID", type = Excel.Type.EXPORT, cellType = Excel.ColumnType.NUMERIC)
    private Long taskId;

    /** 扫描批次ID(一次调度触发扫描到的N个文件共享) */
    private Long scanBatchId;

    /** 文件批次ID(同一文件传输到多个Agent共享) */
    private Long fileBatchId;

    /** 源Agent ID */
    @Excel(name = "源Agent ID")
    private String sourceAgentId;

    /** 源节点名称，格式：user@ip:port */
    @Excel(name = "源节点名称")
    private String sourceAgentName;

    /** 目标Agent ID */
    @Excel(name = "目标Agent ID")
    private String targetAgentId;

    /** 目标节点名称，格式：user@ip:port */
    @Excel(name = "目标节点名称")
    private String targetAgentName;

    /** 源文件完整路径(sourceDir+relativePath) */
    @Excel(name = "源文件路径")
    private String sourcePath;

    /** 目标文件完整路径(targetDir+relativePath) */
    @Excel(name = "目标文件路径")
    private String targetPath;

    /** 文件名(纯文件名,不含路径) */
    @Excel(name = "文件名")
    private String fileName;

    /** 文件大小(字节) */
    @Excel(name = "文件大小(字节)", cellType = Excel.ColumnType.NUMERIC)
    private Long fileSizeBytes;

    /** 文件最后修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "文件修改时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date fileLastModified;

    /** 子任务状态: QUEUED/SENDING/COMPLETED/FAILED/RETRYING */
    @Excel(name = "状态", readConverterExp = "QUEUED=排队中,SENDING=传输中,COMPLETED=已完成,FAILED=失败,RETRYING=重试中")
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
    @Excel(name = "传输速率(B/s)", cellType = Excel.ColumnType.NUMERIC)
    private Long speedBytesPerSec;

    /** 开始传输时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "开始时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    /** 完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "完成时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date completedAt;

    /** 传输耗时(毫秒) */
    @Excel(name = "传输耗时(ms)", cellType = Excel.ColumnType.NUMERIC)
    private Long durationMs;

    /** 错误码 */
    private String errorCode;

    /** 错误详情 */
    private String errorMessage;

    /** 异常堆栈(调试用) */
    private String errorStackTrace;

    /** Agent本地重试次数 */
    @Excel(name = "重试次数", cellType = Excel.ColumnType.NUMERIC)
    private Integer retryCount;

    /** 一对几数量: 1-一对一 2-一对二 N一对N */
    @Excel(name = "目标数", cellType = Excel.ColumnType.NUMERIC)
    private Integer targetCount;

    /** 最后一次重试时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastRetryAt;

    /** 下次可重试时间(Level 2) */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextRetryAfter;

    /** 创建人(系统自动) */
    @Excel(name = "创建人")
    private String createBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "创建时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新人(系统自动) */
    @Excel(name = "更新人")
    private String updateBy;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "更新时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /** 备注 */
    @Excel(name = "备注")
    private String remark;
}
