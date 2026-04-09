package com.cq.panel.admin.server.web.domain.vo.architecture;

import lombok.Data;
import java.io.Serializable;

/**
 * 架构图操作日志 VO
 * 
 * @author cq
 */
@Data
public class ArchDiagramLogVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long diagramId;
    private String operationType;
    private String operationDetail;
    private Long nodeId;
    private Long edgeId;
    private String beforeData;
    private String afterData;
    private String ipAddress;
    private String browser;
    private String os;
    private String createBy;
    private String createTime;
    private String remark;
}