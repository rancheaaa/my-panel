package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;

/**
 * 节点分组表 arch_node_group
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchNodeGroup extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 分组ID */
    private Long id;

    /** 所属架构图ID */
    @Excel(name = "所属架构图ID")
    private Long diagramId;

    /** 分组名称 */
    @Excel(name = "分组名称")
    @NotBlank(message = "分组名称不能为空")
    @Size(max = 200, message = "分组名称不能超过200个字符")
    private String groupName;

    /** 分组编码 */
    @Excel(name = "分组编码")
    @Size(max = 100, message = "分组编码不能超过100个字符")
    private String groupCode;

    /** 分组类型（custom自定义/region区域/layer层级） */
    @Excel(name = "分组类型", readConverterExp = "custom=自定义,region=区域,layer=层级")
    private String groupType;

    /** 分组样式（背景色、边框等）JSON */
    private String groupStyle;

    /** 分组属性JSON */
    private String groupProperties;

    /** 父分组ID（支持嵌套分组） */
    @Excel(name = "父分组ID")
    private Long parentGroupId;

    /** 分组描述 */
    @Excel(name = "分组描述")
    @Size(max = 500, message = "分组描述不能超过500个字符")
    private String description;

    /** 是否可见（0否 1是） */
    @Excel(name = "是否可见", readConverterExp = "0=否,1=是")
    private String visible;

    /** 是否锁定（0否 1是） */
    @Excel(name = "是否锁定", readConverterExp = "0=否,1=是")
    private String locked;

    /** 删除标志（0存在 1删除） */
    private String delFlag;
}