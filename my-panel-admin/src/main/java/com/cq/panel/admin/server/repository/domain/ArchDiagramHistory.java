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
 * 架构图版本历史表 arch_diagram_history
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchDiagramHistory extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 历史ID */
    private Long id;

    /** 架构图ID */
    @Excel(name = "架构图ID")
    private Long diagramId;

    /** 版本号 */
    @Excel(name = "版本号")
    @NotBlank(message = "版本号不能为空")
    @Size(max = 50, message = "版本号不能超过50个字符")
    private String version;

    /** 版本名称 */
    @Excel(name = "版本名称")
    @Size(max = 200, message = "版本名称不能超过200个字符")
    private String versionName;

    /** 版本描述 */
    @Excel(name = "版本描述")
    @Size(max = 500, message = "版本描述不能超过500个字符")
    private String versionDescription;

    /** 完整架构图数据JSON */
    private String diagramData;

    /** 缩略图Base64或URL */
    private String thumbnail;

    /** 变更摘要 */
    @Excel(name = "变更摘要")
    @Size(max = 1000, message = "变更摘要不能超过1000个字符")
    private String changeSummary;

    /** 变更类型（create/update/delete/restore） */
    @Excel(name = "变更类型", readConverterExp = "create=创建,update=修改,delete=删除,restore=恢复")
    private String changeType;

    /** 是否当前版本（0否 1是） */
    @Excel(name = "是否当前版本", readConverterExp = "0=否,1=是")
    private String isCurrent;

    /** 被恢复次数 */
    @Excel(name = "被恢复次数")
    private Integer restoreCount;
}