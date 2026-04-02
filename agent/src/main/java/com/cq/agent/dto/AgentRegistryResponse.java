package com.cq.agent.dto;


import lombok.Data;

/**
 * Agent注册响应DTO
 * 
 * @author cq
 */
@Data
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
}