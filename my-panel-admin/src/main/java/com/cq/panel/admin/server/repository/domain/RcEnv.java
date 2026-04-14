package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;

/**
 * 环境管理对象 rc_env
 * 
 * @author cq
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class RcEnv extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 环境ID */
    private Long id;

    /** 环境名称 */
    @Excel(name = "环境名称")
    @NotBlank(message = "环境名称不能为空")
    @Size(max = 100, message = "环境名称长度不能超过100个字符")
    private String envName;

    /** 环境描述 */
    @Excel(name = "环境描述")
    @Size(max = 200, message = "环境描述长度不能超过200个字符")
    private String envDesc;
}
