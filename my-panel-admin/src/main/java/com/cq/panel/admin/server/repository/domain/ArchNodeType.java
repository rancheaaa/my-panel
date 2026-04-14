package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Date;

/**
 * 节点类型表 arch_node_type
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchNodeType extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 类型ID */
    private Long id;

    /** 类型编码 */
    @Excel(name = "类型编码")
    @NotBlank(message = "类型编码不能为空")
    @Size(max = 100, message = "类型编码不能超过100个字符")
    private String typeCode;

    /** 类型名称 */
    @Excel(name = "类型名称")
    @NotBlank(message = "类型名称不能为空")
    @Size(max = 200, message = "类型名称不能超过200个字符")
    private String typeName;

    /** 图标（SVG或图片URL） */
    @Excel(name = "图标")
    private String icon;

    /** 分类（如：基础设施、中间件、应用服务等） */
    @Excel(name = "分类")
    @Size(max = 100, message = "分类不能超过100个字符")
    private String category;

    /** 默认宽度 */
    @Excel(name = "默认宽度")
    private Integer defaultWidth;

    /** 默认高度 */
    @Excel(name = "默认高度")
    private Integer defaultHeight;

    /** 默认样式模板（颜色、边框等）JSON */
    private String defaultStyle;

    /** 默认属性模板JSON */
    private String defaultProperties;

    /** 属性校验规则JSON */
    private String validationRules;

    /** 是否系统内置（0否 1是） */
    @Excel(name = "是否系统内置", readConverterExp = "0=否,1=是")
    private String isSystem;

    /** 是否启用（0否 1是） */
    @Excel(name = "是否启用", readConverterExp = "0=否,1=是")
    private String isActive;

    /** 排序号 */
    @Excel(name = "排序号")
    private Integer sortOrder;

    /** 删除标志（0存在 1删除） */
    private String delFlag;
}