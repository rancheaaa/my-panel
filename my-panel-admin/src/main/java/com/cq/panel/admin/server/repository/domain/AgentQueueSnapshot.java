package com.cq.panel.admin.server.repository.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
public class AgentQueueSnapshot extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String agentId;
    private Long taskId;
    private Integer sendQueueDepth;
    private Integer sendQueuePeakDepth;
    private Integer sendQueueCapacity;
    private BigDecimal sendQueueUtilizationPct;
    private Long sendQueueAvgWaitMs;
    private Integer retryQueueDepth;
    private Integer retryQueuePeakDepth;
    private Integer retryQueueCapacity;
    private Date retryNextScheduleTime;
    private BigDecimal processingRatePerSec;
    private BigDecimal successRatePct;
    private String congestionLevel;
    private Integer isCongested;
    private String congestionReason;
    private String errorDistribution;
    private String longestWaitingTasks;
    private Date snapshotTime;
}
