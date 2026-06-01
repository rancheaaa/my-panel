package com.cq.panel.admin.server.repository.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
public class BatchTransferTaskImport implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String batchNo;

    private Integer rowNum;

    private String taskName;

    private String taskDescription;

    private String sourceAgentId;

    private String sourceAgentName;

    private String sourceDir;

    private String targetDirs;

    private String includePatterns;

    private String excludePatterns;

    private String scanCronExpression;

    private Integer maxScanFiles;

    private String targetAgentIds;

    private String targetAgentNames;

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

    private String status;

    private Integer scheduledEnabled;

    private String scheduledStartTime;

    private String scheduledEndTime;

    private Integer taskPriority;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    private String validateStatus;

    private String validateMessage;

    private String importStatus;

    private Long importedTaskId;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    private String remark;

    private String fileName;
}
