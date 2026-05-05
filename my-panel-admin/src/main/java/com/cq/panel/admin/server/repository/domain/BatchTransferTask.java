package com.cq.panel.admin.server.repository.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

@Data
public class BatchTransferTask
{
    private Long id;

    private String taskName;

    private String taskDescription;

    private String sourceAgentId;

    private String sourceDir;

    private String targetDirs;

    private String includePatterns;

    private String excludePatterns;

    private Integer scanFrequencySec;

    private String scanCronExpression;

    private Integer maxScanFiles;

    private String targetAgents;

    private Integer maxBandwidthKbS;

    private Integer retryEnabled;

    private Integer retryMaxDays;

    private Integer retryIntervalMin;

    private String postTransferAction;

    private String backupDir;

    private String backupMode;

    private Integer preserveDirStructure;

    private String transferMode;

    private String routingStrategy;

    private String routingConfig;

    private String status;

    private Integer totalFiles;

    private Long totalSizeBytes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    private String createBy;

    private String updateBy;

    private String remark;

    private Integer deleted;
}
