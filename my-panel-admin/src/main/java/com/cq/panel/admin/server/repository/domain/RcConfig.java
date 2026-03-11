package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 配置中心对象 rc_config
 * 
 * @author cq
 */
public class RcConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 配置ID */
    private Long id;

    /** 环境ID */
    @Excel(name = "环境ID")
    private Long envId;

    /** 项目ID */
    @Excel(name = "项目ID")
    private Long projectId;

    /** 配置键 */
    @Excel(name = "配置键")
    private String configKey;

    /** 配置值 */
    @Excel(name = "配置值")
    private String configValue;

    /** 配置描述 */
    @Excel(name = "配置描述")
    private String configDesc;

    /** 来源（0手工新增 1批量导入） */
    @Excel(name = "来源", readConverterExp = "0=手工新增,1=批量导入")
    private String source;

    /** 环境名称 (冗余用于显示) */
    private String envName;

    /** 项目名称 (冗余用于显示) */
    private String projectName;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }
    public void setEnvId(Long envId) 
    {
        this.envId = envId;
    }

    @NotNull(message = "环境不能为空")
    public Long getEnvId() 
    {
        return envId;
    }
    public void setProjectId(Long projectId) 
    {
        this.projectId = projectId;
    }

    @NotNull(message = "项目不能为空")
    public Long getProjectId() 
    {
        return projectId;
    }
    public void setConfigKey(String configKey) 
    {
        this.configKey = configKey;
    }

    @NotBlank(message = "配置键不能为空")
    @Size(min = 0, max = 200, message = "配置键长度不能超过200个字符")
    public String getConfigKey() 
    {
        return configKey;
    }
    public void setConfigValue(String configValue) 
    {
        this.configValue = configValue;
    }

    public String getConfigValue() 
    {
        return configValue;
    }
    public void setConfigDesc(String configDesc) 
    {
        this.configDesc = configDesc;
    }

    @Size(min = 0, max = 200, message = "配置描述长度不能超过200个字符")
    public String getConfigDesc() 
    {
        return configDesc;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getSource()
    {
        return source;
    }

    public String getEnvName() {
        return envName;
    }

    public void setEnvName(String envName) {
        this.envName = envName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("envId", getEnvId())
            .append("projectId", getProjectId())
            .append("configKey", getConfigKey())
            .append("configValue", getConfigValue())
            .append("configDesc", getConfigDesc())
            .append("source", getSource())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
