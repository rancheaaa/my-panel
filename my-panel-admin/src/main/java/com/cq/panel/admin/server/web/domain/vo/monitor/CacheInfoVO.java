package com.cq.panel.admin.server.web.domain.vo.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Data
@Schema(description = "缓存监控信息视图对象")
public class CacheInfoVO {

    @Schema(description = "Redis信息")
    private Properties info;

    @Schema(description = "DB大小", example = "1024")
    private Object dbSize;

    @Schema(description = "命令统计")
    private List<Map<String, String>> commandStats;

    public Properties getInfo() {
        return info;
    }

    public void setInfo(Properties info) {
        this.info = info;
    }

    public Object getDbSize() {
        return dbSize;
    }

    public void setDbSize(Object dbSize) {
        this.dbSize = dbSize;
    }

    public List<Map<String, String>> getCommandStats() {
        return commandStats;
    }

    public void setCommandStats(List<Map<String, String>> commandStats) {
        this.commandStats = commandStats;
    }
}

