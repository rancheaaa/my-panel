package com.cq.panel.admin.server.web.domain.dto.architecture;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

/**
 * 架构图 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "架构图DTO")
public class ArchDiagramDTO {
    @Schema(description = "架构图ID")
    private Long id;

    @Schema(description = "架构图名称")
    @NotBlank(message = "架构图名称不能为空")
    @Size(min = 0, max = 200, message = "架构图名称长度不能超过200个字符")
    private String diagramName;

    @Schema(description = "架构图描述")
    @Size(min = 0, max = 500, message = "架构图描述长度不能超过500个字符")
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

    @Schema(description = "备注")
    private String remark;
}