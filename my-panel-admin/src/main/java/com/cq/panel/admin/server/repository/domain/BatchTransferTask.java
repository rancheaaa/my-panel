package com.cq.panel.admin.server.repository.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;
import java.util.List;

/**
 * 批量传输任务对象 batch_transfer_task
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BatchTransferTask extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long id;

    /** 任务名称 */
    private String taskName;

    /** 任务描述 */
    private String taskDescription;

    /** 源Agent ID */
    private String sourceAgentId;

    /** 源节点名称(格式: user@ip:port) */
    private String sourceAgentName;

    /** 源目录绝对路径 */
    private String sourceDir;

    /** 目标节点目录(分号分隔) */
    private String targetDirs;

    /** 包含通配符(JSON数组) */
    private String includePatterns;

    /** 排除通配符(JSON数组) */
    private String excludePatterns;

    /** 定时扫描Cron表达式 */
    private String scanCronExpression;

    /** 单次最大扫描文件数 */
    private Integer maxScanFiles;

    /** 目标Agent ID列表(JSON数组) */
    private String targetAgentIds;

    /** 目标节点名称列表(JSON数组, 格式: user@ip:port) */
    private String targetAgentNames;

    /** 是否启用自动重试: 0-否 1-是 */
    private Integer retryEnabled;

    /** 重试保留天数 */
    private Integer retryMaxDays;

    /** 首次重试间隔(分钟) */
    private Integer retryIntervalMin;

    /** 单个子任务最大重试次数 */
    private Integer maxRetryCount;

    /** 重试退避策略: LINEAR/EXPONENTIAL */
    private String retryBackoffType;

    /** 传输后操作: NONE/DELETE/BACKUP */
    private String postTransferAction;

    /** 备份目录绝对路径 */
    private String backupDir;

    /** 备份模式: COPY/MOVE */
    private String backupMode;

    /** 是否保持原始目录结构: 0-否 1-是 */
    private Integer preserveDirStructure;

    /** 传输模式: ONE_TO_ONE/ONE_TO_MANY */
    private String transferMode;

    /** 路由策略: BROADCAST/ROUND_ROBIN/REGION_BASED/RANDOM */
    private String routingStrategy;

    /** 路由策略配置JSON */
    private String routingConfig;

    /** 任务运行状态: READY/RUNNING/PAUSED */
    private String status;

    /** 首次启动时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    /** 逻辑删除标志: 0-未删除 1-已删除 */
    private Integer deleted;
}
