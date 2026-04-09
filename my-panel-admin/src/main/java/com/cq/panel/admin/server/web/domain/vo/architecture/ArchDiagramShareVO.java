package com.cq.panel.admin.server.web.domain.vo.architecture;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 架构图分享 VO
 * 
 * @author cq
 */
@Data
public class ArchDiagramShareVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long diagramId;
    private String shareCode;
    private String shareType;
    private String sharePassword;
    private Date expireTime;
    private Integer maxViewCount;
    private Integer currentViewCount;
    private String allowDownload;
    private String allowCopy;
    private String status;
    private String shareBy;
    private String shareTime;
    private String revokeTime;
    private String revokeBy;
}