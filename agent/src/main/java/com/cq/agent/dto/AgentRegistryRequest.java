package com.cq.agent.dto;


/**
 * Agent注册请求DTO
 * 
 * @author cq
 */
public class AgentRegistryRequest {
    
    private String nodeName;
    
    private String osType;
    
    private String appId;
    
    private String agentIp;
    
    private Integer agentPort;
    
    private String remark;

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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    public String toString() {
        return "AgentRegistryRequest{" +
                "nodeName='" + nodeName + '\'' +
                ", osType='" + osType + '\'' +
                ", appId='" + appId + '\'' +
                ", agentIp='" + agentIp + '\'' +
                ", agentPort=" + agentPort +
                ", remark='" + remark + '\'' +
                '}';
    }
}