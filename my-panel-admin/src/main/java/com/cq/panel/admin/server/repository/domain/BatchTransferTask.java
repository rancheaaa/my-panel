package com.cq.panel.admin.server.repository.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
public class BatchTransferTask extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String taskName;
    private String taskDescription;
    private String sourceAgentId;
    private String sourceDir;
    private String targetDirs;
    private String includePatterns;
    private String excludePatterns;
    private Integer scanFrequencySec;
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
    private Integer transferredFiles;
    private Long transferredSizeBytes;
    private Integer failedFiles;
    private Integer postProcessFiles;
    private Integer postProcessFailed;
    private Date startedAt;
    private Date completedAt;
    private Date postProcessedAt;
    private Integer deleted;
}
