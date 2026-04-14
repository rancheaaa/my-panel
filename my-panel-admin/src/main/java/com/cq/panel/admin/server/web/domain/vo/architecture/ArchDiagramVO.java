package com.cq.panel.admin.server.web.domain.vo.architecture;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

/**
 * 架构图 VO
 * 
 * @author cq
 */
@Data
@Schema(description = "架构图VO")
public class ArchDiagramVO {
    @Schema(description = "架构图ID")
    private Long id;

    @Schema(description = "架构图名称")
    private String diagramName;

    @Schema(description = "架构图描述")
    private String diagramDescription;

    @Schema(description = "版本号")
    private String diagramVersion;

    @Schema(description = "缩略图Base64或URL")
    private String thumbnail;

    @Schema(description = "画布配置（缩放比例、背景等）JSON")
    private String canvasConfig;

    @Schema(description = "状态（0草稿 1已发布 2已归档）")
    private String status;

    @Schema(description = "是否已发布（0否 1是）")
    private String isPublished;

    @Schema(description = "发布时间")
    private Date publishedAt;

    @Schema(description = "创建者")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新者")
    private String updateBy;

    @Schema(description = "更新时间")
    private Date updateTime;

    @Schema(description = "备注")
    private String remark;
}