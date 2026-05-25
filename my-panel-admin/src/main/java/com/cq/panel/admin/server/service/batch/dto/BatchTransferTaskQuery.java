package com.cq.panel.admin.server.service.batch.dto;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "批量传输任务查询条件")
public class BatchTransferTaskQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "任务主键ID(精准匹配)")
    private String id;

    @Schema(description = "任务状态: READY/RUNNING/PAUSED(精确匹配)")
    private String status;

    @Schema(description = "源Agent ID(精确匹配)")
    private String sourceAgentId;

    @Schema(description = "源节点名称(模糊搜索)")
    private String sourceAgentName;

    @Schema(description = "任务名称(模糊搜索)")
    private String taskName;

    @Schema(description = "任务描述(模糊搜索)")
    private String taskDescription;

    @Schema(description = "源目录(模糊搜索)")
    private String sourceDir;

    @Schema(description = "目标Agent ID(模糊搜索)")
    private String targetAgentId;

    @Schema(description = "目标节点名称(模糊搜索)")
    private String targetAgentName;

    @Schema(description = "目标目录(模糊搜索)")
    private String targetDir;

    @Schema(description = "传输模式: ONE_TO_ONE/ONE_TO_MANY(精确匹配)")
    private String transferMode;

    @Schema(description = "路由策略: BROADCAST/ROUND_ROBIN/REGION_BASED/RANDOM(精确匹配)")
    private String routingStrategy;

    @Schema(description = "传输后操作: NONE/DELETE/BACKUP(精确匹配)")
    private String postTransferAction;

    @Schema(description = "是否启用重试: 0-否 1-是(精确匹配)")
    private Integer retryEnabled;

    @Schema(description = "保持目录结构: 0-否 1-是(精确匹配)")
    private Integer preserveDirStructure;

    @Schema(description = "页码", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;
}
