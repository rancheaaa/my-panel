package com.cq.panel.admin.server.web.domain.vo.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统相关信息
 * 
 * @author cq
 */
@Data
@Schema(description = "系统相关信息")
public class SysVO {
    @Schema(description = "服务器名称", example = "Production-Server-01")
    private String computerName;

    @Schema(description = "服务器Ip", example = "192.168.1.100")
    private String computerIp;

    @Schema(description = "项目路径", example = "/opt/application")
    private String userDir;

    @Schema(description = "操作系统", example = "Linux")
    private String osName;

    @Schema(description = "系统架构", example = "amd64")
    private String osArch;

    public String getComputerName() {
        return computerName;
    }

    public void setComputerName(String computerName) {
        this.computerName = computerName;
    }

    public String getComputerIp() {
        return computerIp;
    }

    public void setComputerIp(String computerIp) {
        this.computerIp = computerIp;
    }

    public String getUserDir() {
        return userDir;
    }

    public void setUserDir(String userDir) {
        this.userDir = userDir;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getOsArch() {
        return osArch;
    }

    public void setOsArch(String osArch) {
        this.osArch = osArch;
    }
}

