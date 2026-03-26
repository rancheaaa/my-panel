package com.cq.agent.dto;


/**
 * Agent注册响应DTO
 * 
 * @author cq
 */
public class AgentRegistryResponse {
    
    private String id;
    
    private String nodeName;
    
    private String osType;
    
    private String appId;
    
    private String agentIp;
    
    private Integer agentPort;
    
    private Integer nodeEnabled;
    
    private Integer nodeStatus;
    
    private String remark;
    
    private String createTime;
    
    private String updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getOsType() {
        return osType;
    }

    public void setOsType(String osType) {
        this.osType = osType;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAgentIp() {
        return agentIp;
    }

    public void setAgentIp(String agentIp) {
        this.agentIp = agentIp;
    }

    public Integer getAgentPort() {
        return agentPort;
    }

    public void setAgentPort(Integer agentPort) {
        this.agentPort = agentPort;
    }

    public Integer getNodeEnabled() {
        return nodeEnabled;
    }

    public void setNodeEnabled(Integer nodeEnabled) {
        this.nodeEnabled = nodeEnabled;
    }

    public Integer getNodeStatus() {
        return nodeStatus;
    }

    public void setNodeStatus(Integer nodeStatus) {
        this.nodeStatus = nodeStatus;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "AgentRegistryResponse{" +
                "id='" + id + '\'' +
                ", nodeName='" + nodeName + '\'' +
                ", osType='" + osType + '\'' +
                ", appId='" + appId + '\'' +
                ", agentIp='" + agentIp + '\'' +
                ", agentPort=" + agentPort +
                ", nodeEnabled=" + nodeEnabled +
                ", nodeStatus=" + nodeStatus +
                ", remark='" + remark + '\'' +
                ", createTime='" + createTime + '\'' +
                ", updateTime='" + updateTime + '\'' +
                '}';
    }
}