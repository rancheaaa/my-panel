package com.cq.panel.admin.server.web.domain.vo.monitor;

import com.cq.panel.admin.server.common.utils.Arith;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * CPU相关信息
 * 
 * @author cq
 */
@Data
@Schema(description = "CPU相关信息")
public class CpuVO {
    @Schema(description = "核心数", example = "4")
    private int cpuNum;

    @Schema(description = "CPU总的使用率", example = "10.5")
    private double total;

    @Schema(description = "CPU系统使用率", example = "5.2")
    private double sys;

    @Schema(description = "CPU用户使用率", example = "5.3")
    private double used;

    @Schema(description = "CPU当前等待率", example = "0.0")
    private double wait;

    @Schema(description = "CPU当前空闲率", example = "89.5")
    private double free;

    public int getCpuNum() {
        return cpuNum;
    }

    public void setCpuNum(int cpuNum) {
        this.cpuNum = cpuNum;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public void setSys(double sys) {
        this.sys = sys;
    }

    public void setUsed(double used) {
        this.used = used;
    }

    public void setWait(double wait) {
        this.wait = wait;
    }

    public void setFree(double free) {
        this.free = free;
    }

    public double getTotal() {
        return Arith.round(Arith.mul(total, 100), 2);
    }

    public double getSys() {
        return Arith.round(Arith.mul(sys / total, 100), 2);
    }

    public double getUsed() {
        return Arith.round(Arith.mul(used / total, 100), 2);
    }

    public double getWait() {
        return Arith.round(Arith.mul(wait / total, 100), 2);
    }

    public double getFree() {
        return Arith.round(Arith.mul(free / total, 100), 2);
    }
}

