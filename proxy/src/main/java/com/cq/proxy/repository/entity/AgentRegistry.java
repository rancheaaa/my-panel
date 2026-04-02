package com.cq.proxy.repository.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AgentRegistry {

    private String id;
    private String nodeName;
    private String osType;
    private String appId;
    private String agentIp;
    private Integer agentPort;
    private Integer nodeEnabled;
    private Integer nodeStatus;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}