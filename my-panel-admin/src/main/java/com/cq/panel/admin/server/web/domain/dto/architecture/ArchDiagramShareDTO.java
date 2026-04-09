package com.cq.panel.admin.server.web.domain.dto.architecture;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 架构图分享 DTO
 * 
 * @author cq
 */
@Data
public class ArchDiagramShareDTO implements Serializable {
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
}