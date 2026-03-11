package com.cq.panel.admin.server.web.domain.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 在线用户查询对象
 * 
 * @author cq
 */
@Data
@Schema(description = "在线用户查询对象")
public class SysUserOnlineQueryDTO {

    @Schema(description = "登录IP地址")
    private String ipaddr;

    @Schema(description = "用户名称")
    private String userName;

    public String getIpaddr() {
        return ipaddr;
    }

    public void setIpaddr(String ipaddr) {
        this.ipaddr = ipaddr;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}

