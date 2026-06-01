package com.cq.panel.admin.server.web.domain.dto.batch;

import lombok.Data;

import java.util.List;

/**
 * 批量传输任务数据传输对象
 */
@Data
public class BatchTransferTaskCreateDTO {

    private String taskName;
    private String taskDescription;
    private String sourceAgentId;
    private String sourceAgentName;
    private String sourceDir;
    private String targetDirs;
    private List<String> includePatterns;
    private List<String> excludePatterns;
    private String scanCronExpression;
    private Integer maxScanFiles;
    private List<String> targetAgentIds;
    private List<String> targetAgentNames;
    private Integer retryEnabled;
    private Integer retryMaxDays;
    private Integer retryIntervalMin;
    private Integer maxRetryCount;
    private String retryBackoffType;
    private String postTransferAction;
    private String backupDir;
    private String backupMode;
    private Integer preserveDirStructure;
    private String transferMode;
    private String routingStrategy;
    private String routingConfig;
    private Integer scheduledEnabled;
    private String scheduledStartTime;
    private String scheduledEndTime;
    private Integer taskPriority;
    private String remark;
}
