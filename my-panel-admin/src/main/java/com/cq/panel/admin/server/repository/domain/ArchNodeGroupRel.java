package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Date;

/**
 * 节点分组关联表 arch_node_group_rel
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchNodeGroupRel extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联ID */
    private Long id;

    /** 分组ID */
    @Excel(name = "分组ID")
    @NotNull(message = "分组ID不能为空")
    private Long groupId;

    /** 节点ID */
    @Excel(name = "节点ID")
    @NotNull(message = "节点ID不能为空")
    private Long nodeId;
}