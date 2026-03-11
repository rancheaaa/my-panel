package com.cq.panel.admin.server.web.domain.vo.monitor;

import com.cq.panel.admin.server.common.utils.Arith;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.lang.management.ManagementFactory;

/**
 * JVM相关信息
 * 
 * @author cq
 */
@Data
@Schema(description = "JVM相关信息")
public class JvmVO {
    @Schema(description = "当前JVM占用的内存总数(M)", example = "512.0")
    private double total;

    @Schema(description = "JVM最大可用内存总数(M)", example = "4096.0")
    private double max;

    @Schema(description = "JVM空闲内存(M)", example = "256.0")
    private double free;

    @Schema(description = "JDK版本", example = "17.0.2")
    private String version;

    @Schema(description = "JDK路径", example = "/usr/java/jdk-17")
    private String home;

    public void setTotal(double total) {
        this.total = total;
    }

    public void setMax(double max) {
        this.max = max;
    }

    public void setFree(double free) {
        this.free = free;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public double getTotal() {
        return Arith.div(total, (1024 * 1024), 2);
    }

    public double getMax() {
        return Arith.div(max, (1024 * 1024), 2);
    }

    public double getFree() {
        return Arith.div(free, (1024 * 1024), 2);
    }

    @Schema(description = "JVM已用内存(M)")
    public double getUsed() {
        return Arith.div(total - free, (1024 * 1024), 2);
    }

    @Schema(description = "JVM使用率")
    public double getUsage() {
        return Arith.mul(Arith.div(total - free, total, 4), 100);
    }

    @Schema(description = "JDK名称")
    public String getName() {
        return ManagementFactory.getRuntimeMXBean().getVmName();
    }
}

