package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Date;

/**
 * 架构图评论表 arch_diagram_comment
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchDiagramComment extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 评论ID */
    private Long id;

    /** 架构图ID */
    @Excel(name = "架构图ID")
    private Long diagramId;

    /** 关联节点ID（NULL表示评论整个架构图） */
    @Excel(name = "关联节点ID")
    private Long nodeId;

    /** 关联边缘ID */
    @Excel(name = "关联边缘ID")
    private Long edgeId;

    /** 父评论ID（支持回复） */
    @Excel(name = "父评论ID")
    private Long parentCommentId;

    /** 评论内容 */
    @Excel(name = "评论内容")
    @NotBlank(message = "评论内容不能为空")
    private String commentContent;

    /** 评论位置坐标JSON（用于在画布上显示评论标记） */
    private String commentPosition;

    /** 评论类型（text文本/image图片/file文件） */
    @Excel(name = "评论类型", readConverterExp = "text=文本,image=图片,file=文件")
    private String commentType;

    /** 附件信息JSON */
    private String attachments;

    /** 是否已解决（0否 1是） */
    @Excel(name = "是否已解决", readConverterExp = "0=否,1=是")
    private String isResolved;

    /** 解决时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date resolvedTime;

    /** 解决人 */
    private String resolvedBy;

    /** 点赞数 */
    @Excel(name = "点赞数")
    private Integer likeCount;

    /** 回复数 */
    @Excel(name = "回复数")
    private Integer replyCount;

    /** 状态（0正常 1已删除） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=已删除")
    private String status;

    /** 删除标志（0存在 1删除） */
    private String delFlag;
}