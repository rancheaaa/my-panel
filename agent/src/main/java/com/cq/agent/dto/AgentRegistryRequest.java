package com.cq.agent.dto;


import lombok.Data;

/**
 * Agent注册请求DTO
 * 
 * @author cq
 */
@Data
public class AgentRegistryRequest {
    
    private String nodeName;
    
    private String osType;
    
    private String appId;
    
    private String agentIp;
    
    private Integer agentPort;
    
    private String remark;
}