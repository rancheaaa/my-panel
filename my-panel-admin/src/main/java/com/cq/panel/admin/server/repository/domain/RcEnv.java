package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serial;

/**
 * 环境管理对象 rc_env
 * 
 * @author cq
 */
public class RcEnv extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 环境ID */
    private Long id;

    /** 环境名称 */
    @Excel(name = "环境名称")
    private String envName;

    /** 环境描述 */
    @Excel(name = "环境描述")
    private String envDesc;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }
    public void setEnvName(String envName) 
    {
        this.envName = envName;
    }

    @NotBlank(message = "环境名称不能为空")
    @Size(max = 100, message = "环境名称长度不能超过100个字符")
    public String getEnvName() 
    {
        return envName;
    }
    public void setEnvDesc(String envDesc) 
    {
        this.envDesc = envDesc;
    }

    @Size(max = 200, message = "环境描述长度不能超过200个字符")
    public String getEnvDesc() 
    {
        return envDesc;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("envName", getEnvName())
            .append("envDesc", getEnvDesc())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
