package com.cq.panel.admin.server.repository.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
public class BatchTransferSubtask extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long taskId;
    private String filePath;
    private String fileName;
    private Long fileSizeBytes;
    private String fileMd5;
    private Date fileLastModified;
    private String targetAgentId;
    private String status;
    private String transferId;
    private Integer transferredChunks;
    private Integer totalChunks;
    private Long transferredBytes;
    private Long speedBytesPerSec;
    private Date startedAt;
    private Date completedAt;
    private Long durationMs;
    private String errorCode;
    private String errorMessage;
    private String errorStackTrace;
    private Integer retryCount;
    private Integer proxyRetryCount;
    private Date lastRetryAt;
    private Date nextRetryAfter;
}
