package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import java.io.Serial;

/**
 * Agent注册信息对象 agent_registry
 * 
 * @author cq
 */
public class AgentRegistry extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 节点ID */
    private String id;

    /** 节点名称 */
    @Excel(name = "节点名称")
    private String nodeName;

    /** 所属操作系统 */
    @Excel(name = "操作系统")
    private String osType;

    /** 所属应用ID */
    @Excel(name = "应用ID")
    private String appId;

    /** Agent IP地址 */
    @Excel(name = "Agent IP")
    private String agentIp;

    /** Agent端口 */
    @Excel(name = "Agent端口")
    private Integer agentPort;

    /** 节点是否启用 */
    @Excel(name = "节点启用", readConverterExp = "0=启用,1=临时关闭,2=永久关闭")
    private Integer nodeEnabled;

    /** 节点状态 */
    @Excel(name = "节点状态", readConverterExp = "0=离线,1=在线,2=未知")
    private Integer nodeStatus;

    /** 备注信息 */
    @Excel(name = "备注")
    private String remark;

    public void setId(String id) 
    {
        this.id = id;
    }

    public String getId() 
    {
        return id;
    }

    public void setNodeName(String nodeName) 
    {
        this.nodeName = nodeName;
    }

    public String getNodeName() 
    {
        return nodeName;
    }

    public void setOsType(String osType) 
    {
        this.osType = osType;
    }

    public String getOsType() 
    {
        return osType;
    }

    public void setAppId(String appId) 
    {
        this.appId = appId;
    }

    public String getAppId() 
    {
        return appId;
    }

    public void setAgentIp(String agentIp) 
    {
        this.agentIp = agentIp;
    }

    public String getAgentIp() 
    {
        return agentIp;
    }

    public void setAgentPort(Integer agentPort) 
    {
        this.agentPort = agentPort;
    }

    public Integer getAgentPort() 
    {
        return agentPort;
    }

    public void setNodeEnabled(Integer nodeEnabled) 
    {
        this.nodeEnabled = nodeEnabled;
    }

    public Integer getNodeEnabled() 
    {
        return nodeEnabled;
    }

    public void setNodeStatus(Integer nodeStatus) 
    {
        this.nodeStatus = nodeStatus;
    }

    public Integer getNodeStatus() 
    {
        return nodeStatus;
    }

    public void setRemark(String remark) 
    {
        this.remark = remark;
    }

    public String getRemark() 
    {
        return remark;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("nodeName", getNodeName())
            .append("osType", getOsType())
            .append("appId", getAppId())
            .append("agentIp", getAgentIp())
            .append("agentPort", getAgentPort())
            .append("nodeEnabled", getNodeEnabled())
            .append("nodeStatus", getNodeStatus())
            .append("remark", getRemark())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}