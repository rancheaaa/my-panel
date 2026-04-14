package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;

/**
 * 架构图标签表 arch_diagram_tag
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchDiagramTag extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 标签ID */
    private Long id;

    /** 标签名称 */
    @Excel(name = "标签名称")
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 100, message = "标签名称不能超过100个字符")
    private String tagName;

    /** 标签颜色 */
    @Excel(name = "标签颜色")
    @Size(max = 20, message = "标签颜色不能超过20个字符")
    private String tagColor;

    /** 标签类型（system系统/custom自定义） */
    @Excel(name = "标签类型", readConverterExp = "system=系统,custom=自定义")
    private String tagType;

    /** 标签默认值 */
    @Excel(name = "标签默认值")
    @Size(max = 255, message = "标签默认值不能超过255个字符")
    private String tagDefaultValue;

    /** 删除标志（0存在 1删除） */
    private String delFlag;
}