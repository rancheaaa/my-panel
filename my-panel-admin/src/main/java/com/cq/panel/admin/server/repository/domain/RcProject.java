package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serial;

/**
 * 应用管理对象 rc_project
 * 
 * @author cq
 */
public class RcProject extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 应用ID */
    private Long id;

    /** 应用名称 */
    @Excel(name = "应用名称")
    private String projectName;

    /** 应用描述 */
    @Excel(name = "应用描述")
    private String projectDesc;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }
    public void setProjectName(String projectName) 
    {
        this.projectName = projectName;
    }

    @NotBlank(message = "应用名称不能为空")
    @Size(max = 100, message = "应用名称长度不能超过100个字符")
    public String getProjectName() 
    {
        return projectName;
    }
    public void setProjectDesc(String projectDesc) 
    {
        this.projectDesc = projectDesc;
    }

    @Size(max = 200, message = "应用描述长度不能超过200个字符")
    public String getProjectDesc() 
    {
        return projectDesc;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("projectName", getProjectName())
            .append("projectDesc", getProjectDesc())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
