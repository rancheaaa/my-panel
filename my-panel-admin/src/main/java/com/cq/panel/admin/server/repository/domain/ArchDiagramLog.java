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
 * 架构图操作日志表 arch_diagram_log
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchDiagramLog extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long id;

    /** 架构图ID */
    @Excel(name = "架构图ID")
    private Long diagramId;

    /** 操作类型（create创建/update修改/delete删除/publish发布/unpublish取消发布/share分享/export导出/import导入） */
    @Excel(name = "操作类型", readConverterExp = "create=创建,update=修改,delete=删除,publish=发布,unpublish=取消发布,share=分享,export=导出,import=导入")
    @NotBlank(message = "操作类型不能为空")
    @Size(max = 50, message = "操作类型不能超过50个字符")
    private String operationType;

    /** 操作详情JSON */
    private String operationDetail;

    /** 关联节点ID */
    @Excel(name = "关联节点ID")
    private Long nodeId;

    /** 关联边缘ID */
    @Excel(name = "关联边缘ID")
    private Long edgeId;

    /** 操作前数据JSON */
    private String beforeData;

    /** 操作后数据JSON */
    private String afterData;

    /** IP地址 */
    @Excel(name = "IP地址")
    @Size(max = 128, message = "IP地址不能超过128个字符")
    private String ipAddress;

    /** 浏览器 */
    @Excel(name = "浏览器")
    @Size(max = 50, message = "浏览器不能超过50个字符")
    private String browser;

    /** 操作系统 */
    @Excel(name = "操作系统")
    @Size(max = 50, message = "操作系统不能超过50个字符")
    private String os;
}