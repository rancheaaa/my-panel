package com.cq.panel.admin.server.web.domain.vo.architecture;

import lombok.Data;
import java.io.Serializable;

/**
 * 架构图版本历史 VO
 * 
 * @author cq
 */
@Data
public class ArchDiagramHistoryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long diagramId;
    private String version;
    private String versionName;
    private String changeSummary;
    private String snapshotData;
    private Long snapshotSize;
    private String isCurrent;
    private String status;
    private String createBy;
    private String createTime;
    private String remark;
}