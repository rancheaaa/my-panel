package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;

/**
 * AccessToken管理对象 rc_access_token
 *
 * @author cq
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class RcAccessToken extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    private Long id;

    /**
     * Token值
     */
    @Excel(name = "Token值")
    @NotBlank(message = "Token值不能为空")
    @Size(max = 200, message = "Token值长度不能超过200个字符")
    private String tokenValue;

    /**
     * Token描述
     */
    @Excel(name = "Token描述")
    @Size(max = 200, message = "Token描述长度不能超过200个字符")
    private String tokenDesc;

    /**
     * 状态（0启用 1禁用）
     */
    @Excel(name = "状态", readConverterExp = "0=启用,1=禁用")
    private String status;
}
