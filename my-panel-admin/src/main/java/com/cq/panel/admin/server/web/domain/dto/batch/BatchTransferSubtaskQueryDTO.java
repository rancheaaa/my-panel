package com.cq.panel.admin.server.web.domain.dto.batch;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "批量传输子任务查询条件")
public class BatchTransferSubtaskQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "任务ID(精确匹配)")
    private Long taskId;

    @Schema(description = "状态(精确匹配): QUEUED/SENDING/COMPLETED/FAILED/RETRYING")
    private String status;

    @Schema(description = "源文件路径(模糊搜索)")
    private String sourceFilePath;

    @Schema(description = "目标文件路径(模糊搜索)")
    private String targetFilePath;

    @Schema(description = "目标Agent ID(模糊搜索)")
    private String targetAgentId;

    @Schema(description = "源Agent ID(模糊搜索)")
    private String sourceAgentId;

    @Schema(description = "源节点名称(模糊搜索)")
    private String sourceAgentName;

    @Schema(description = "目标节点名称(模糊搜索)")
    private String targetAgentName;

    @Schema(description = "文件名(模糊搜索)")
    private String fileName;

    @Schema(description = "扫描批次ID(精确匹配)")
    private Long scanBatchId;

    @Schema(description = "文件批次ID(精确匹配)")
    private Long fileBatchId;

    @Schema(description = "页码", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;
}