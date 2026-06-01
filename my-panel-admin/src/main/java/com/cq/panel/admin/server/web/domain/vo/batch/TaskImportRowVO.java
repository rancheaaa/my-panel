package com.cq.panel.admin.server.web.domain.vo.batch;

import lombok.Data;

@Data
public class TaskImportRowVO {

    private int rowNum;

    private String transferMode;

    private String taskName;

    private String taskDescription;

    private String sourceAgentName;

    private String sourceDir;

    private String targetAgentNames;

    private String targetDirs;

    private String includePatterns;

    private String excludePatterns;

    private String scanCronExpression;

    private Integer maxScanFiles;

    private Integer retryEnabled;

    private Integer retryMaxDays;

    private Integer retryIntervalMin;

    private Integer maxRetryCount;

    private String retryBackoffType;

    private String postTransferAction;

    private String backupDir;

    private String backupMode;

    private Integer preserveDirStructure;

    private String routingStrategy;

    private String routingConfig;

    private Integer scheduledEnabled;

    private String scheduledStartTime;

    private String scheduledEndTime;

    private Integer taskPriority;

    private String remark;

    private String validateStatus;

    private String validateMessage;

    private String importStatus;
}
