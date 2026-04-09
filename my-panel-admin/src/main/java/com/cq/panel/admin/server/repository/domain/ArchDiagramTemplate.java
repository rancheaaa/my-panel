package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.math.BigDecimal;

/**
 * 架构图模板表 arch_diagram_template
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchDiagramTemplate extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板ID */
    private Long id;

    /** 模板名称 */
    @Excel(name = "模板名称")
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称不能超过200个字符")
    private String templateName;

    /** 模板描述 */
    @Excel(name = "模板描述")
    @Size(max = 500, message = "模板描述不能超过500个字符")
    private String templateDescription;

    /** 模板分类（如：微服务、大数据、传统架构等） */
    @Excel(name = "模板分类")
    @Size(max = 100, message = "模板分类不能超过100个字符")
    private String templateCategory;

    /** 缩略图Base64或URL */
    private String thumbnail;

    /** 模板数据JSON（包含节点、边缘等） */
    private String templateData;

    /** 预览图Base64或URL */
    private String previewImage;

    /** 标签（逗号分隔） */
    @Excel(name = "标签")
    @Size(max = 500, message = "标签不能超过500个字符")
    private String tags;

    /** 是否系统内置（0否 1是） */
    @Excel(name = "是否系统内置", readConverterExp = "0=否,1=是")
    private String isSystem;

    /** 是否公开（0否 1是） */
    @Excel(name = "是否公开", readConverterExp = "0=否,1=是")
    private String isPublic;

    /** 使用次数 */
    @Excel(name = "使用次数")
    private Integer useCount;

    /** 评分（0-5分） */
    @Excel(name = "评分")
    private BigDecimal rating;

    /** 评分人数 */
    @Excel(name = "评分人数")
    private Integer ratingCount;

    /** 状态（0正常 1禁用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=禁用")
    private String status;

    /** 删除标志（0存在 1删除） */
    private String delFlag;
}