package com.cq.panel.admin.server.web.domain.vo.architecture;

import lombok.Data;
import java.io.Serializable;

/**
 * 架构图评论 VO
 * 
 * @author cq
 */
@Data
public class ArchDiagramCommentVO implements Serializable {
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
    private String resolvedTime;
    private String resolvedBy;
    private Integer likeCount;
    private Integer replyCount;
    private String status;
    private String createBy;
    private String createTime;
    private String updateTime;
    private String delFlag;
}