package com.cq.panel.admin.server.repository.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

@Data
public class BatchTransferStatistics
{
    private Long id;

    private Long taskId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date snapshotTime;

    private Integer totalSubtasks;

    private Integer completedCount;

    private Integer failedCount;

    private Integer runningCount;

    private Integer queuedCount;

    private Integer retryingCount;

    private Integer cancelledCount;

    private Long totalSizeBytes;

    private Long transferredBytes;

    private Integer transferredFiles;

    private Integer failedFiles;

    private Long remainingBytes;

    private BigDecimal progressPercent;

    private Long avgSpeedBytesPerSec;

    private Long peakSpeedBytesPerSec;

    private Long currentSpeedBytesPerSec;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date firstFileStartedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastActivityAt;

    private Long totalElapsedMs;

    private Long avgDurationPerFileMs;

    private Integer postProcessCompleted;

    private Integer postProcessFailed;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date postProcessedAt;

    private Integer totalRetryCount;

    private Integer successfulRetryCount;

    private Integer maxSingleFileRetries;

    private BigDecimal avgRetryCount;

    private String targetAgentStats;

    private String errorTypeDistribution;

    private String topErrorCode;

    private String topErrorMessage;

    private Integer etaSeconds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date estimatedCompletionAt;

    private Integer dataVersion;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
