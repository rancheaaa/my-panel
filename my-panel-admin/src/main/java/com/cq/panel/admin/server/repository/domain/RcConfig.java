package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serial;

/**
 * 配置中心对象 rc_config
 * 
 * @author cq
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class RcConfig extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 配置ID */
    private Long id;

    /** 环境ID */
    @Excel(name = "环境ID")
    @NotNull(message = "环境不能为空")
    private Long envId;

    /** 项目ID */
    @Excel(name = "项目ID")
    @NotNull(message = "项目不能为空")
    private Long projectId;

    /** 配置键 */
    @Excel(name = "配置键")
    @NotBlank(message = "配置键不能为空")
    @Size(max = 200, message = "配置键长度不能超过200个字符")
    private String configKey;

    /** 配置值 */
    @Excel(name = "配置值")
    private String configValue;

    /** 配置描述 */
    @Excel(name = "配置描述")
    @Size(max = 200, message = "配置描述长度不能超过200个字符")
    private String configDesc;

    /** 来源（0手工新增 1批量导入） */
    @Excel(name = "来源", readConverterExp = "0=手工新增,1=批量导入")
    private String source;

    /** 环境名称 (冗余用于显示) */
    private String envName;

    /** 项目名称 (冗余用于显示) */
    private String projectName;
}
