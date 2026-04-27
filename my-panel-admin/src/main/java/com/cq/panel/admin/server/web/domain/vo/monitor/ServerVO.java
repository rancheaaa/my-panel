package com.cq.panel.admin.server.web.domain.vo.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * 服务器相关信息
 * 
 * @author cq
 */
@Data
@Schema(description = "服务器相关信息")
public class ServerVO {
    @Schema(description = "CPU相关信息")
    private CpuVO cpu = new CpuVO();

    @Schema(description = "內存相关信息")
    private MemVO mem = new MemVO();

    @Schema(description = "JVM相关信息")
    private JvmVO jvm = new JvmVO();

    @Schema(description = "服务器相关信息")
    private SysVO sys = new SysVO();

    @Schema(description = "磁盘相关信息")
    private List<SysFileVO> sysFiles = new LinkedList<>();
}

