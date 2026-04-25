package com.cq.panel.admin.server.web.domain.dto.architecture;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 节点 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "节点DTO")
public class ArchNodeDTO {
    @Schema(description = "节点ID")
    private Long id;

    @Schema(description = "前端临时ID（用于保存时映射）")
    @JsonIgnore
    private String frontId;

    @Schema(description = "所属架构图ID")
    @NotNull(message = "所属架构图ID不能为空")
    private Long diagramId;

    @Schema(description = "节点类型ID")
    private Long nodeTypeId;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "节点编码/标识")
    private String nodeCode;

    @Schema(description = "节点类型编码")
    private String nodeType;

    @Schema(description = "X坐标位置")
    private BigDecimal xPosition;

    @Schema(description = "Y坐标位置")
    private BigDecimal yPosition;

    @Schema(description = "X坐标位置 (前端别名)")
    private BigDecimal positionX;

    @Schema(description = "Y坐标位置 (前端别名)")
    private BigDecimal positionY;

    @Schema(description = "节点宽度")
    private Integer nodeWidth;

    @Schema(description = "节点高度")
    private Integer nodeHeight;

    @Schema(description = "Z轴层级（用于图层顺序）")
    private Integer zIndex;

    @Schema(description = "旋转角度")
    private BigDecimal rotation;

    @Schema(description = "节点样式（颜色、边框、阴影等）JSON")
    private String nodeStyle;

    @Schema(description = "节点属性（业务属性）JSON")
    private String nodeProperties;

    @Schema(description = "IP地址 (前端传参)")
    private String ip;

    @Schema(description = "端口 (前端传参)")
    private String port;

    @Schema(description = "配置信息 (前端传参)")
    private String config;

    @Schema(description = "标签信息 (前端传参)")
    private String tags;

    @Schema(description = "节点元数据（扩展属性）JSON")
    private String nodeMeta;

    @Schema(description = "节点显示标签")
    private String label;

    @Schema(description = "节点描述")
    private String description;

    @Schema(description = "状态（0正常 1禁用 2异常）")
    private String status;

    @Schema(description = "是否锁定（0否 1是）")
    private String locked;

    @Schema(description = "是否可见（0否 1是）")
    private String visible;

    @Schema(description = "备注")
    private String remark;
}