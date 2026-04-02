package com.cq.proxy.repository.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class RcConfig {

    private Long id;
    private Long envId;
    private Long projectId;
    private String configKey;
    private String configValue;
    private String configDesc;
    private String source;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;

}