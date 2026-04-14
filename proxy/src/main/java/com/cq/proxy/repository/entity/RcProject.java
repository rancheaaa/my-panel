package com.cq.proxy.repository.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class RcProject {

    private Long id;
    private String projectName;
    private String projectDesc;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}