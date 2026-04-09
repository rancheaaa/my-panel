package com.cq.panel.admin.server.web.domain.dto.architecture;

import lombok.Data;
import java.io.Serializable;

/**
 * 架构图评论 DTO
 * 
 * @author cq
 */
@Data
public class ArchDiagramCommentDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long diagramId;
    private Long nodeId;
    private Long edgeId;
    private Long parentCommentId;
    private String commentContent;
    private String commentPosition;
    private String commentType;
    private String attachments;
    private String isResolved;
    private String status;
}