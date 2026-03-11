package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * AccessToken管理对象 rc_access_token
 * 
 * @author cq
 */
public class RcAccessToken extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** Token值 */
    @Excel(name = "Token值")
    private String tokenValue;

    /** Token描述 */
    @Excel(name = "Token描述")
    private String tokenDesc;

    /** 状态（0启用 1禁用） */
    @Excel(name = "状态", readConverterExp = "0=启用,1=禁用")
    private String status;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }
    public void setTokenValue(String tokenValue) 
    {
        this.tokenValue = tokenValue;
    }

    @NotBlank(message = "Token值不能为空")
    @Size(min = 0, max = 200, message = "Token值长度不能超过200个字符")
    public String getTokenValue() 
    {
        return tokenValue;
    }
    public void setTokenDesc(String tokenDesc) 
    {
        this.tokenDesc = tokenDesc;
    }

    @Size(min = 0, max = 200, message = "Token描述长度不能超过200个字符")
    public String getTokenDesc() 
    {
        return tokenDesc;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("tokenValue", getTokenValue())
            .append("tokenDesc", getTokenDesc())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
