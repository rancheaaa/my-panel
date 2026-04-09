package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Date;

/**
 * 架构图分享表 arch_diagram_share
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchDiagramShare extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 分享ID */
    private Long id;

    /** 架构图ID */
    @Excel(name = "架构图ID")
    private Long diagramId;

    /** 分享码 */
    @Excel(name = "分享码")
    @NotBlank(message = "分享码不能为空")
    @Size(max = 100, message = "分享码不能超过100个字符")
    private String shareCode;

    /** 分享类型（view查看/edit编辑/comment评论） */
    @Excel(name = "分享类型", readConverterExp = "view=查看,edit=编辑,comment=评论")
    private String shareType;

    /** 分享密码（加密存储） */
    private String sharePassword;

    /** 过期时间（NULL表示永不过期） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireTime;

    /** 最大查看次数（NULL表示无限制） */
    @Excel(name = "最大查看次数")
    private Integer maxViewCount;

    /** 当前查看次数 */
    @Excel(name = "当前查看次数")
    private Integer currentViewCount;

    /** 是否允许下载（0否 1是） */
    @Excel(name = "是否允许下载", readConverterExp = "0=否,1=是")
    private String allowDownload;

    /** 是否允许复制（0否 1是） */
    @Excel(name = "是否允许复制", readConverterExp = "0=否,1=是")
    private String allowCopy;

    /** 状态（0有效 1已失效 2已撤销） */
    @Excel(name = "状态", readConverterExp = "0=有效,1=已失效,2=已撤销")
    private String status;

    /** 分享人 */
    @Excel(name = "分享人")
    private String shareBy;

    /** 分享时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date shareTime;

    /** 撤销时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date revokeTime;

    /** 撤销人 */
    private String revokeBy;
}