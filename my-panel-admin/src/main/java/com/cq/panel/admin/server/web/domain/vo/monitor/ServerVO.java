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
    private List<SysFileVO> sysFiles = new LinkedList<SysFileVO>();

    public CpuVO getCpu() {
        return cpu;
    }

    public void setCpu(CpuVO cpu) {
        this.cpu = cpu;
    }

    public MemVO getMem() {
        return mem;
    }

    public void setMem(MemVO mem) {
        this.mem = mem;
    }

    public JvmVO getJvm() {
        return jvm;
    }

    public void setJvm(JvmVO jvm) {
        this.jvm = jvm;
    }

    public SysVO getSys() {
        return sys;
    }

    public void setSys(SysVO sys) {
        this.sys = sys;
    }

    public List<SysFileVO> getSysFiles() {
        return sysFiles;
    }

    public void setSysFiles(List<SysFileVO> sysFiles) {
        this.sysFiles = sysFiles;
    }
}

