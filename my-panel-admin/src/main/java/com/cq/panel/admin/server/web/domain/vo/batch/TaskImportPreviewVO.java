package com.cq.panel.admin.server.web.domain.vo.batch;

import lombok.Data;

import java.util.List;

@Data
public class TaskImportPreviewVO {

    private String batchNo;

    private int total;

    private int passCount;

    private int failCount;

    private List<TaskImportRowVO> rows;
}
