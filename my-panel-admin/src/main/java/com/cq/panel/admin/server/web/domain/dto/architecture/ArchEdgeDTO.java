package com.cq.panel.admin.server.web.domain.dto.architecture;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 边缘 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "边缘DTO")
public class ArchEdgeDTO {
    @Schema(description = "边缘ID")
    private Long id;

    @Schema(description = "所属架构图ID")
    @NotNull(message = "所属架构图ID不能为空")
    private Long diagramId;

    @Schema(description = "边缘类型（如：default、dashed、dotted等）")
    private String edgeType;

    @Schema(description = "源节点ID")
    @NotNull(message = "源节点ID不能为空")
    private Long sourceNodeId;

    @Schema(description = "目标节点ID")
    @NotNull(message = "目标节点ID不能为空")
    private Long targetNodeId;

    @Schema(description = "源锚点位置（top/bottom/left/right/auto）")
    private String sourceAnchor;

    @Schema(description = "目标锚点位置（top/bottom/left/right/auto）")
    private String targetAnchor;

    @Schema(description = "边缘标签")
    private String edgeLabel;

    @Schema(description = "边缘样式（颜色、线宽、箭头等）JSON")
    private String edgeStyle;

    @Schema(description = "边缘属性JSON")
    private String edgeProperties;

    @Schema(description = "边缘元数据（扩展属性）JSON")
    private String edgeMeta;

    @Schema(description = "权重（用于布局算法）")
    private BigDecimal weight;

    @Schema(description = "是否有动画（0否 1是）")
    private String animated;

    @Schema(description = "状态（0正常 1禁用）")
    private String status;

    @Schema(description = "是否可见（0否 1是）")
    private String visible;

    @Schema(description = "备注")
    private String remark;
}