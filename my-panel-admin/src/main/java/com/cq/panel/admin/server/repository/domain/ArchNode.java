package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.annotation.Excel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.math.BigDecimal;

/**
 * 节点主表 arch_node
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchNode extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 节点ID */
    private Long id;

    /** 所属架构图ID */
    @NotNull(message = "所属架构图ID不能为空")
    private Long diagramId;

    /** 节点类型ID */
    private Long nodeTypeId;

    /** 节点名称 */
    @Excel(name = "节点名称")
    @NotBlank(message = "节点名称不能为空")
    @Size(max = 200, message = "节点名称不能超过200个字符")
    private String nodeName;

    /** 节点类型编码 (用于展示) */
    private String nodeType;

    /** 节点编码/标识 */
    @Excel(name = "节点编码")
    @Size(max = 100, message = "节点编码不能超过100个字符")
    private String nodeCode;

    /** X坐标位置 */
    private BigDecimal xPosition;

    /** Y坐标位置 */
    private BigDecimal yPosition;

    /** 节点宽度 */
    private Integer nodeWidth;

    /** 节点高度 */
    private Integer nodeHeight;

    /** Z轴层级（用于图层顺序） */
    private Integer zIndex;

    /** 旋转角度 */
    private BigDecimal rotation;

    /** 节点样式（颜色、边框、阴影等）JSON */
    private String nodeStyle;

    /** 节点属性（业务属性）JSON */
    private String nodeProperties;

    /** 节点元数据（扩展属性）JSON */
    private String nodeMeta;

    /** 节点显示标签 */
    @Size(max = 200, message = "节点显示标签不能超过200个字符")
    private String label;

    /** 节点描述 */
    @Size(max = 500, message = "节点描述不能超过500个字符")
    private String description;

    /** 状态（0正常 1禁用 2异常） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=禁用,2=异常")
    private String status;

    /** 是否锁定（0否 1是） */
    private String locked;

    /** 是否可见（0否 1是） */
    private String visible;

    /** 删除标志（0存在 1删除） */
    private String delFlag;
}