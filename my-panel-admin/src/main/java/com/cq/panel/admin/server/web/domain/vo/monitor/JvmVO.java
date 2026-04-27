package com.cq.panel.admin.server.web.domain.vo.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

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

    @Schema(description = "JVM已用内存(M)")
    private double used;

    @Schema(description = "JVM使用率")
    private double usage;

    @Schema(description = "JDK版本", example = "17.0.2")
    private String version;

    @Schema(description = "JDK路径", example = "/usr/java/jdk-17")
    private String home;

    @Schema(description = "JDK名称")
    private String name;

    @Schema(description = "进程ID")
    private Long pid;

    @Schema(description = "启动时间")
    private String startTime;

    @Schema(description = "运行时长")
    private String runTime;

    @Schema(description = "运行参数")
    private String inputArgs;
}

