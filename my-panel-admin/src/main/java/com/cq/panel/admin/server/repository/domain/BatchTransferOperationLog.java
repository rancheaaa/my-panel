package com.cq.panel.admin.server.repository.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
public class BatchTransferOperationLog implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long taskId;
    private String operatorId;
    private String operatorName;
    private String operatorIp;
    private String operationType;
    private String oldConfig;
    private String newConfig;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date operationTime;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
