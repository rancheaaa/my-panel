package com.cq.panel.admin.server.web.domain.vo.batch;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Schema(description = "批量子任务VO")
public class BatchSubtaskVO
{
    @Schema(description = "子任务ID")
    private Long id;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "文件相对路径")
    private String filePath;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小(字节)")
    private Long fileSizeBytes;

    @Schema(description = "文件MD5")
    private String fileMd5;

    @Schema(description = "目标Agent ID")
    private String targetAgentId;

    @Schema(description = "子任务状态码")
    private String status;

    @Schema(description = "子任务状态描述")
    private String statusLabel;

    @Schema(description = "底层分块传输会话ID")
    private String transferId;

    @Schema(description = "已传输分块数")
    private Integer transferredChunks;

    @Schema(description = "总分块数")
    private Integer totalChunks;

    @Schema(description = "已传输字节数")
    private Long transferredBytes;

    @Schema(description = "当前传输速率(字节/秒)")
    private Long speedBytesPerSec;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间")
    private Date startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "完成时间")
    private Date completedAt;

    @Schema(description = "传输耗时(毫秒)")
    private Long durationMs;

    @Schema(description = "错误码")
    private String errorCode;

    @Schema(description = "错误详情")
    private String errorMessage;

    @Schema(description = "Agent本地重试次数")
    private Integer retryCount;

    @Schema(description = "Proxy调度层重试次数")
    private Integer proxyRetryCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最后一次重试时间")
    private Date lastRetryAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "下次可重试时间")
    private Date nextRetryAfter;

    @Schema(description = "进度百分比")
    private BigDecimal progressPercent;
}
