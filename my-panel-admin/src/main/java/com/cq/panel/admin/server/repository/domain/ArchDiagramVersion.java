package com.cq.panel.admin.server.repository.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 架构图版本主表
 *
 * @author
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchDiagramVersion extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 版本ID
     */
    private Long id;

    /**
     * 架构图ID
     */
    private Long diagramId;

    /**
     * 版本号
     */
    private String version;

    /**
     * 版本名称
     */
    private String versionName;

    /**
     * 版本描述
     */
    private String versionDescription;

    /**
     * 变更摘要
     */
    private String changeSummary;

    /**
     * 创建者
     */
    private String createBy;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 被恢复次数
     */
    private Integer restoreCount;

    /**
     * 是否当前版本（0否 1是）
     */
    private String isCurrent;
}