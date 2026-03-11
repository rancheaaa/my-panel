package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import java.util.Date;

/**
 * 注册中心节点对象 rc_node
 * 
 * @author cq
 */
public class RcNode extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 节点ID */
    private Long id;

    /** 环境ID */
    @Excel(name = "环境ID")
    private Long envId;

    /** 项目ID */
    @Excel(name = "项目ID")
    private Long projectId;

    /** 节点IP */
    @Excel(name = "节点IP")
    private String nodeIp;

    /** 节点端口 */
    @Excel(name = "节点端口")
    private Integer nodePort;

    /** 状态（0在线 1离线） */
    @Excel(name = "状态", readConverterExp = "0=在线,1=离线")
    private String status;

    /** 最后刷新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "最后刷新时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date lastRefreshTime;

    /** 环境名称 */
    private String envName;

    /** 项目名称 */
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

    public Long getEnvId() 
    {
        return envId;
    }
    public void setProjectId(Long projectId) 
    {
        this.projectId = projectId;
    }

    public Long getProjectId() 
    {
        return projectId;
    }
    public void setNodeIp(String nodeIp) 
    {
        this.nodeIp = nodeIp;
    }

    public String getNodeIp() 
    {
        return nodeIp;
    }
    public void setNodePort(Integer nodePort) 
    {
        this.nodePort = nodePort;
    }

    public Integer getNodePort() 
    {
        return nodePort;
    }
    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }
    public void setLastRefreshTime(Date lastRefreshTime) 
    {
        this.lastRefreshTime = lastRefreshTime;
    }

    public Date getLastRefreshTime() 
    {
        return lastRefreshTime;
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
            .append("nodeIp", getNodeIp())
            .append("nodePort", getNodePort())
            .append("status", getStatus())
            .append("lastRefreshTime", getLastRefreshTime())
            .append("createTime", getCreateTime())
            .toString();
    }
}
