package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;

/**
 * 岗位表 sys_post
 * 
 * @author cq
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class SysPost extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 岗位序号 */
    @Excel(name = "岗位序号", cellType = Excel.ColumnType.NUMERIC)
    private Long postId;

    /** 岗位编码 */
    @Excel(name = "岗位编码")
    @NotBlank(message = "岗位编码不能为空")
    @Size(max = 64, message = "岗位编码长度不能超过64个字符")
    private String postCode;

    /** 岗位名称 */
    @Excel(name = "岗位名称")
    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 50, message = "岗位名称长度不能超过50个字符")
    private String postName;

    /** 岗位排序 */
    @Excel(name = "岗位排序")
    @NotNull(message = "显示顺序不能为空")
    private Integer postSort;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 用户是否存在此岗位标识 默认不存在 */
    private boolean flag = false;
}
