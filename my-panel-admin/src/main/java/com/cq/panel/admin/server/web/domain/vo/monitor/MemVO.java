package com.cq.panel.admin.server.web.domain.vo.monitor;

import com.cq.panel.admin.server.common.utils.Arith;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 內存相关信息
 * 
 * @author cq
 */
@Data
@Schema(description = "內存相关信息")
public class MemVO {
    @Schema(description = "内存总量", example = "16.0")
    private double total;

    @Schema(description = "已用内存", example = "8.5")
    private double used;

    @Schema(description = "剩余内存", example = "7.5")
    private double free;

    public void setTotal(double total) {
        this.total = total;
    }

    public void setUsed(double used) {
        this.used = used;
    }

    public void setFree(double free) {
        this.free = free;
    }

    public double getTotal() {
        return Arith.div(total, (1024 * 1024 * 1024), 2);
    }

    public double getUsed() {
        return Arith.div(used, (1024 * 1024 * 1024), 2);
    }

    public double getFree() {
        return Arith.div(free, (1024 * 1024 * 1024), 2);
    }

    @Schema(description = "内存使用率")
    public double getUsage() {
        return Arith.mul(Arith.div(used, total, 4), 100);
    }
}

