package com.cq.panel.admin.server.web.domain.dto.batch;

import com.cq.panel.admin.server.annotation.Excel;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class BatchTaskImportDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Excel(name = "传输模式", readConverterExp = "ONE_TO_ONE=一对一,ONE_TO_MANY=一对多", combo = {"ONE_TO_ONE", "ONE_TO_MANY"})
    private String transferMode;

    @Excel(name = "任务名称")
    private String taskName;

    @Excel(name = "任务描述")
    private String taskDescription;

    @Excel(name = "源节点名称")
    private String sourceAgentName;

    @Excel(name = "源目录")
    private String sourceDir;

    @Excel(name = "目标节点名称")
    private String targetAgentNames;

    @Excel(name = "目标目录")
    private String targetDirs;

    @Excel(name = "包含模式")
    private String includePatterns;

    @Excel(name = "排除模式")
    private String excludePatterns;

    @Excel(name = "执行频率")
    private String scanCronExpression;

    @Excel(name = "最大扫描文件数", cellType = Excel.ColumnType.NUMERIC)
    private Integer maxScanFiles;

    @Excel(name = "启用重试", readConverterExp = "0=否,1=是", combo = {"0", "1"})
    private Integer retryEnabled;

    @Excel(name = "重试保留天数", cellType = Excel.ColumnType.NUMERIC)
    private Integer retryMaxDays;

    @Excel(name = "重试间隔(分钟)", cellType = Excel.ColumnType.NUMERIC)
    private Integer retryIntervalMin;

    @Excel(name = "最大重试次数", cellType = Excel.ColumnType.NUMERIC)
    private Integer maxRetryCount;

    @Excel(name = "重试退避策略", readConverterExp = "LINEAR=线性,EXPONENTIAL=指数", combo = {"LINEAR", "EXPONENTIAL"})
    private String retryBackoffType;

    @Excel(name = "传输后操作", readConverterExp = "NONE=无操作,DELETE=删除源文件,BACKUP=备份", combo = {"NONE", "DELETE", "BACKUP"})
    private String postTransferAction;

    @Excel(name = "备份目录")
    private String backupDir;

    @Excel(name = "备份模式", readConverterExp = "COPY=复制,MOVE=移动", combo = {"COPY", "MOVE"})
    private String backupMode;

    @Excel(name = "保持目录结构", readConverterExp = "0=否,1=是", combo = {"0", "1"})
    private Integer preserveDirStructure;

    @Excel(name = "路由策略", readConverterExp = "ROUND_ROBIN=轮询,RANDOM=随机,REGION_BASED=区域,BROADCAST=广播", combo = {"BROADCAST", "ROUND_ROBIN", "RANDOM", "REGION_BASED"})
    private String routingStrategy;

    @Excel(name = "路由配置")
    private String routingConfig;

    @Excel(name = "定时传输", readConverterExp = "0=否,1=是", combo = {"0", "1"})
    private Integer scheduledEnabled;

    @Excel(name = "定时开始时间")
    private String scheduledStartTime;

    @Excel(name = "定时结束时间")
    private String scheduledEndTime;

    @Excel(name = "优先级", cellType = Excel.ColumnType.NUMERIC)
    private Integer taskPriority;

    @Excel(name = "备注")
    private String remark;
}
