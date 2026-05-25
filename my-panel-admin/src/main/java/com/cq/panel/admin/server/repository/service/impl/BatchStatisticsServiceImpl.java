package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.repository.mapper.BatchTransferSubtaskMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper;
import com.cq.panel.admin.server.repository.service.IBatchStatisticsService;
import com.cq.panel.admin.server.web.domain.vo.batch.SubtaskSummaryVO;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskSummaryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BatchStatisticsServiceImpl implements IBatchStatisticsService {

    private final BatchTransferSubtaskMapper subtaskMapper;
    private final BatchTransferTaskMapper taskMapper;

    public BatchStatisticsServiceImpl(BatchTransferSubtaskMapper subtaskMapper,
                                       BatchTransferTaskMapper taskMapper) {
        this.subtaskMapper = subtaskMapper;
        this.taskMapper = taskMapper;
    }

    private static Object getFromMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value != null) {
            return value;
        }
        String upperKey = key.toUpperCase();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static long getLong(Map<String, Object> map, String key) {
        Object value = getFromMap(map, key);
        if (value == null) {
            return 0L;
        }
        return ((Number) value).longValue();
    }

    private static double getDouble(Map<String, Object> map, String key) {
        Object value = getFromMap(map, key);
        if (value == null) {
            return 0D;
        }
        return ((Number) value).doubleValue();
    }

    private static String getString(Map<String, Object> map, String key) {
        Object value = getFromMap(map, key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    @Override
    public SubtaskSummaryVO getSubtaskSummary() {
        SubtaskSummaryVO vo = new SubtaskSummaryVO();

        Map<String, Object> summary = subtaskMapper.countSummary();
        log.debug("[BatchStatistics] subtaskSummary raw map keys: {}", summary.keySet());

        vo.setTotalCount(getLong(summary, "totalCount"));
        vo.setTotalSizeBytes(getLong(summary, "totalSizeBytes"));
        vo.setCompletedCount(getLong(summary, "completedCount"));
        vo.setCompletedSizeBytes(getLong(summary, "completedSizeBytes"));
        vo.setSendingCount(getLong(summary, "sendingCount"));
        vo.setFailedCount(getLong(summary, "failedCount"));
        vo.setQueuedCount(getLong(summary, "queuedCount"));
        vo.setRetryingCount(getLong(summary, "retryingCount"));

        long totalTransferred = getLong(summary, "totalTransferredBytes");
        double avgSpeed = getDouble(summary, "avgSpeedBytesPerSec");

        if (vo.getTotalSizeBytes() != null && vo.getTotalSizeBytes() > 0) {
            BigDecimal progress = BigDecimal.valueOf(totalTransferred)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(vo.getTotalSizeBytes()), 2, RoundingMode.HALF_UP);
            vo.setProgressPercent(progress);
        } else {
            vo.setProgressPercent(BigDecimal.ZERO);
        }

        if (avgSpeed > 0) {
            vo.setAvgSpeedBytesPerSec(BigDecimal.valueOf(avgSpeed).setScale(2, RoundingMode.HALF_UP));
        } else {
            vo.setAvgSpeedBytesPerSec(BigDecimal.ZERO);
        }

        List<Map<String, Object>> statusList = subtaskMapper.countGroupByStatus();
        Map<String, Long> statusDistribution = new HashMap<>();
        for (Map<String, Object> item : statusList) {
            String status = getString(item, "status");
            Long count = getLong(item, "count");
            if (status != null) {
                statusDistribution.put(status, count);
            }
        }
        vo.setStatusDistribution(statusDistribution);

        Long todayCount = subtaskMapper.countToday();
        vo.setTodayCount(todayCount != null ? todayCount : 0L);

        return vo;
    }

    @Override
    public TaskSummaryVO getTaskSummary() {
        TaskSummaryVO vo = new TaskSummaryVO();

        Map<String, Object> summary = taskMapper.countSummary();
        log.debug("[BatchStatistics] taskSummary raw map keys: {}", summary.keySet());

        vo.setTotalCount(getLong(summary, "totalCount"));
        vo.setReadyCount(getLong(summary, "readyCount"));
        vo.setRunningCount(getLong(summary, "runningCount"));
        vo.setPausedCount(getLong(summary, "pausedCount"));
        vo.setActiveCount(vo.getRunningCount());

        List<Map<String, Object>> statusList = taskMapper.countGroupByStatus();
        Map<String, Long> statusDistribution = new HashMap<>();
        for (Map<String, Object> item : statusList) {
            String status = getString(item, "status");
            Long count = getLong(item, "count");
            if (status != null) {
                statusDistribution.put(status, count);
            }
        }
        vo.setStatusDistribution(statusDistribution);

        Long todayCount = taskMapper.countToday();
        vo.setTodayCount(todayCount != null ? todayCount : 0L);

        return vo;
    }
}
