package com.cq.panel.admin.server.web.domain.vo.batch;

import lombok.Data;

@Data
public class TaskImportBatchVO {

    private String batchNo;

    private String fileName;

    private String createTime;

    private int total;

    private int passCount;

    private int failCount;

    private String importStatus;
}
