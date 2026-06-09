package com.cq.panel.admin.server.web.domain.dto.proxy;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Agent目录详情检查响应数据
 * 对应Agent端 GET /api/file/dir-check?path= 返回的data字段
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentDirCheckData {

    /** 目录是否存在 */
    private Boolean exists;

    /** 是否为目录 */
    private Boolean isDirectory;

    /** 是否可读 */
    private Boolean canRead;

    /** 是否可写 */
    private Boolean canWrite;

    /** 是否可执行 */
    private Boolean canExecute;

    /** POSIX权限字符串 */
    private String posixPermissions;

    /** 磁盘总空间(字节) */
    private Long diskTotal;

    /** 可用空间(字节) */
    private Long diskUsable;

    /** 空闲空间(字节) */
    private Long diskFree;

    /** 磁盘总空间(MB) */
    private Long diskTotalMB;

    /** 可用空间(MB) */
    private Long diskUsableMB;

    /** 空闲空间(MB) */
    private Long diskFreeMB;

    /** 磁盘空间是否充足 */
    private Boolean diskSufficient;

    /** 错误信息 */
    private String errorMessage;
}
