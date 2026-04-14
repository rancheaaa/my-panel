package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Date;

/**
 * 架构图主表 arch_diagram
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchDiagram extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 架构图ID */
    private Long id;

    /** 架构图名称 */
    @Excel(name = "架构图名称")
    @NotBlank(message = "架构图名称不能为空")
    @Size(max = 200, message = "架构图名称不能超过200个字符")
    private String diagramName;

    /** 架构图描述 */
    @Excel(name = "架构图描述")
    @Size(max = 500, message = "架构图描述不能超过500个字符")
    private String diagramDescription;

    /** 版本号 */
    @Excel(name = "版本号")
    private String diagramVersion;

    /** 缩略图Base64或URL */
    private String thumbnail;

    /** 画布配置（缩放比例、背景等）JSON */
    private String canvasConfig;

    /** 状态（0草稿 1已发布 2已归档） */
    @Excel(name = "状态", readConverterExp = "0=草稿,1=已发布,2=已归档")
    private String status;

    /** 是否已发布（0否 1是） */
    @Excel(name = "是否已发布", readConverterExp = "0=否,1=是")
    private String isPublished;

    /** 发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishedAt;

    /** 删除标志（0存在 1删除） */
    private String delFlag;
}