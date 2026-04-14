package com.cq.proxy.repository.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class RcNode {

    private Long id;
    private Long envId;
    private Long projectId;
    private String nodeIp;
    private Integer nodePort;
    private String status;
    private String zone;
    private LocalDateTime lastRefreshTime;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}