package com.cq.panel.admin.server.web.domain.vo.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统文件相关信息
 * 
 * @author cq
 */
@Data
@Schema(description = "系统文件相关信息")
public class SysFileVO {
    @Schema(description = "盘符路径", example = "/")
    private String dirName;

    @Schema(description = "盘符类型", example = "ext4")
    private String sysTypeName;

    @Schema(description = "文件类型", example = "local")
    private String typeName;

    @Schema(description = "总大小", example = "100GB")
    private String total;

    @Schema(description = "剩余大小", example = "40GB")
    private String free;

    @Schema(description = "已经使用量", example = "60GB")
    private String used;

    @Schema(description = "资源的使用率", example = "60.0")
    private double usage;

    public void setDirName(String dirName) {
        this.dirName = dirName;
    }

    public void setSysTypeName(String sysTypeName) {
        this.sysTypeName = sysTypeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public void setTotal(String total) {
        this.total = total;
    }

    public void setFree(String free) {
        this.free = free;
    }

    public void setUsed(String used) {
        this.used = used;
    }

    public void setUsage(double usage) {
        this.usage = usage;
    }
}

