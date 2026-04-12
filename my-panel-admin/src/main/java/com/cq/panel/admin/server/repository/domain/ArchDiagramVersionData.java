package com.cq.panel.admin.server.repository.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 架构图版本数据表
 *
 * @author
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchDiagramVersionData extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 数据ID
     */
    private Long id;

    /**
     * 版本ID
     */
    private Long versionId;

    /**
     * 数据类型（nodes/edges）
     */
    private String dataType;

    /**
     * 数据内容（JSON格式）
     */
    private String dataContent;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}