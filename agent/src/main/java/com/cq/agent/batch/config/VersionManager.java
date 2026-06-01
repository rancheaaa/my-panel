package com.cq.agent.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 版本管理器
 * 管理每个任务的配置版本号，用于判断是否需要更新配置
 * 基于时间戳的版本比较机制
 */
public class VersionManager {

    private static final Logger log = LoggerFactory.getLogger(VersionManager.class);
    
    private final Map<Long, Long> taskVersions = new ConcurrentHashMap<>();

    /**
     * 接受新版本
     * @param taskId 任务ID
     * @param newVersion 新版本号（通常为时间戳）
     * @return true表示版本被接受（首次或更新），false表示被忽略（相同或更低）
     */
    public boolean acceptVersion(Long taskId, Long newVersion) {
        final boolean[] accepted = {false};
        
        taskVersions.compute(taskId, (key, current) -> {
            if (current == null) {
                log.info("✅ 首次接收: taskId={}, version={}", key, newVersion);
                accepted[0] = true;
                return newVersion;
            } else if (newVersion > current) {
                log.info("✅ 版本更新: taskId={}, {} → {}", key, current, newVersion);
                accepted[0] = true;
                return newVersion;
            } else if (newVersion.equals(current)) {
                log.debug("相同版本（幂等）: taskId={}, version={}", key, current);
                accepted[0] = false;
                return current;
            } else {
                log.warn("⚠️  低版本被忽略: taskId={}, current={}, received={}", 
                    key, current, newVersion);
                accepted[0] = false;
                return current;
            }
        });
        
        return accepted[0];
    }

    /**
     * 获取任务当前版本
     * @param taskId 任务ID
     * @return 当前版本号，不存在返回-1
     */
    public long getCurrentVersion(Long taskId) {
        return taskVersions.getOrDefault(taskId, -1L);
    }

    /**
     * 清除所有版本信息
     */
    public void clear() {
        taskVersions.clear();
        log.info("✅ 所有版本已清除");
    }

    /**
     * 获取管理的任务数量
     */
    public int getTaskCount() {
        return taskVersions.size();
    }
}
