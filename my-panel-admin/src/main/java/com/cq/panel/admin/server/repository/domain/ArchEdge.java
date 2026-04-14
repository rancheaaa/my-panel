package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.annotation.Excel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.math.BigDecimal;

/**
 * 边缘关系表 arch_edge
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchEdge extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 边缘ID */
    private Long id;

    /** 所属架构图ID */
    @NotNull(message = "所属架构图ID不能为空")
    private Long diagramId;

    /** 边缘类型（如：default、dashed、dotted等） */
    @Excel(name = "边缘类型")
    @Size(max = 100, message = "边缘类型不能超过100个字符")
    private String edgeType;

    /** 源节点ID */
    @NotNull(message = "源节点ID不能为空")
    private Long sourceNodeId;

    /** 目标节点ID */
    @NotNull(message = "目标节点ID不能为空")
    private Long targetNodeId;

    /** 源锚点位置（top/bottom/left/right/auto） */
    @Size(max = 50, message = "源锚点位置不能超过50个字符")
    private String sourceAnchor;

    /** 目标锚点位置（top/bottom/left/right/auto） */
    @Size(max = 50, message = "目标锚点位置不能超过50个字符")
    private String targetAnchor;

    /** 边缘标签 */
    @Size(max = 200, message = "边缘标签不能超过200个字符")
    private String edgeLabel;

    /** 边缘样式（颜色、线宽、箭头等）JSON */
    private String edgeStyle;

    /** 边缘属性JSON */
    private String edgeProperties;

    /** 边缘元数据（扩展属性）JSON */
    private String edgeMeta;

    /** 权重（用于布局算法） */
    private BigDecimal weight;

    /** 是否有动画（0否 1是） */
    private String animated;

    /** 状态（0正常 1禁用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=禁用")
    private String status;

    /** 是否可见（0否 1是） */
    private String visible;

    /** 删除标志（0存在 1删除） */
    private String delFlag;
}