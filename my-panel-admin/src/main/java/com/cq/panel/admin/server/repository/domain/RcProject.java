package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;

/**
 * 应用管理对象 rc_project
 * 
 * @author cq
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class RcProject extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 应用ID */
    private Long id;

    /** 应用名称 */
    @Excel(name = "应用名称")
    @NotBlank(message = "应用名称不能为空")
    @Size(max = 100, message = "应用名称长度不能超过100个字符")
    private String projectName;

    /** 应用描述 */
    @Excel(name = "应用描述")
    @Size(max = 200, message = "应用描述长度不能超过200个字符")
    private String projectDesc;
}
