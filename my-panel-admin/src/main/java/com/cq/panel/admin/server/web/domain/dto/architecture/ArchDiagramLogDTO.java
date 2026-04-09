package com.cq.panel.admin.server.web.domain.dto.architecture;

import lombok.Data;
import java.io.Serializable;

/**
 * 架构图操作日志 DTO
 * 
 * @author cq
 */
@Data
public class ArchDiagramLogDTO implements Serializable {
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
    private String remark;
}