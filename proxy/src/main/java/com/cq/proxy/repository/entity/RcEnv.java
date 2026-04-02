package com.cq.proxy.repository.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class RcEnv {

    private Long id;
    private String envName;
    private String envDesc;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}