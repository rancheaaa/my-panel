package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Date;

/**
 * 架构图标签关联表 arch_diagram_tag_rel
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchDiagramTagRel extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联ID */
    private Long id;

    /** 架构图ID */
    @Excel(name = "架构图ID")
    @NotNull(message = "架构图ID不能为空")
    private Long diagramId;

    /** 节点ID */
    @Excel(name = "节点ID")
    @NotNull(message = "节点ID不能为空")
    private Long nodeId;

    /** 标签ID */
    @Excel(name = "标签ID")
    @NotNull(message = "标签ID不能为空")
    private Long tagId;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}