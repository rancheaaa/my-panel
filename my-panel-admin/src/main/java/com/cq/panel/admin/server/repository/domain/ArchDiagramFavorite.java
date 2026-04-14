package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Date;

/**
 * 架构图收藏表 arch_diagram_favorite
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchDiagramFavorite extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 收藏ID */
    private Long id;

    /** 架构图ID */
    @Excel(name = "架构图ID")
    @NotNull(message = "架构图ID不能为空")
    private Long diagramId;

    /** 用户ID */
    @Excel(name = "用户ID")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 收藏夹名称 */
    @Excel(name = "收藏夹名称")
    @Size(max = 100, message = "收藏夹名称不能超过100个字符")
    private String folderName;

    /** 收藏时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}