package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.annotation.Excel;
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
    @Excel(name = "任务ID", type = Excel.Type.EXPORT)
    private Long id;

    /** 任务名称 */
    @Excel(name = "任务名称")
    private String taskName;

    /** 任务描述 */
    @Excel(name = "任务描述")
    private String taskDescription;

    /** 源Agent ID */
    @Excel(name = "源Agent ID")
    private String sourceAgentId;

    /** 源节点名称(格式: user@ip:port) */
    @Excel(name = "源节点名称")
    private String sourceAgentName;

    /** 源目录绝对路径 */
    @Excel(name = "源目录")
    private String sourceDir;

    /** 目标节点目录(分号分隔) */
    private String targetDirs;

    /** 包含通配符(JSON数组) */
    private String includePatterns;

    /** 排除通配符(JSON数组) */
    private String excludePatterns;

    /** 定时扫描Cron表达式 */
    @Excel(name = "执行频率")
    private String scanCronExpression;

    /** 单次最大扫描文件数 */
    @Excel(name = "最大扫描文件数", cellType = Excel.ColumnType.NUMERIC)
    private Integer maxScanFiles;

    /** 目标Agent ID列表(JSON数组) */
    private String targetAgentIds;

    /** 目标节点名称列表(JSON数组, 格式: user@ip:port) */
    @Excel(name = "目标节点名称")
    private String targetAgentNames;

    /** 是否启用自动重试: 0-否 1-是 */
    @Excel(name = "启用重试", readConverterExp = "0=否,1=是")
    private Integer retryEnabled;

    /** 重试保留天数 */
    @Excel(name = "重试保留天数", cellType = Excel.ColumnType.NUMERIC)
    private Integer retryMaxDays;

    /** 首次重试间隔(分钟) */
    @Excel(name = "重试间隔(分钟)", cellType = Excel.ColumnType.NUMERIC)
    private Integer retryIntervalMin;

    /** 单个子任务最大重试次数 */
    @Excel(name = "最大重试次数", cellType = Excel.ColumnType.NUMERIC)
    private Integer maxRetryCount;

    /** 重试退避策略: LINEAR/EXPONENTIAL */
    @Excel(name = "重试退避策略", readConverterExp = "LINEAR=线性,EXPONENTIAL=指数")
    private String retryBackoffType;

    /** 传输后操作: NONE/DELETE/BACKUP */
    @Excel(name = "传输后操作", readConverterExp = "NONE=无操作,DELETE=删除源文件,BACKUP=备份")
    private String postTransferAction;

    /** 备份目录绝对路径 */
    @Excel(name = "备份目录")
    private String backupDir;

    /** 备份模式: COPY/MOVE */
    @Excel(name = "备份模式", readConverterExp = "COPY=复制,MOVE=移动")
    private String backupMode;

    /** 是否保持原始目录结构: 0-否 1-是 */
    @Excel(name = "保持目录结构", readConverterExp = "0=否,1=是")
    private Integer preserveDirStructure;

    /** 传输模式: ONE_TO_ONE/ONE_TO_MANY */
    @Excel(name = "传输模式", readConverterExp = "ONE_TO_ONE=一对一,ONE_TO_MANY=一对多")
    private String transferMode;

    /** 路由策略: BROADCAST/ROUND_ROBIN/REGION_BASED/RANDOM */
    @Excel(name = "路由策略", readConverterExp = "ROUND_ROBIN=轮询,RANDOM=随机,REGION_BASED=区域,BROADCAST=广播")
    private String routingStrategy;

    /** 路由策略配置JSON */
    private String routingConfig;

    /** 任务运行状态: READY/RUNNING/PAUSED */
    @Excel(name = "任务状态", readConverterExp = "READY=就绪,RUNNING=运行中,PAUSED=已暂停,STOPPED=已停止,COMPLETED=已完成,ERROR=异常")
    private String status;

    /** 是否开启定时传输: 0-否 1-是 */
    @Excel(name = "定时传输", readConverterExp = "0=否,1=是")
    private Integer scheduledEnabled;

    /** 定时传输开始时间(HH:mm:ss) */
    @Excel(name = "定时开始时间")
    private String scheduledStartTime;

    /** 定时传输结束时间(HH:mm:ss) */
    @Excel(name = "定时结束时间")
    private String scheduledEndTime;

    /** 任务优先级: 1-最高 10-最低, 默认5 */
    @Excel(name = "优先级", cellType = Excel.ColumnType.NUMERIC)
    private Integer taskPriority;

    /** 首次启动时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "首次启动时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    /** 逻辑删除标志: 0-未删除 1-已删除 */
    private Integer deleted;

    /** 创建时间 */
    @Excel(name = "创建时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 创建人 */
    @Excel(name = "创建人")
    private String createBy;

    /** 更新时间 */
    @Excel(name = "更新时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /** 更新人 */
    @Excel(name = "更新人")
    private String updateBy;
}
