package com.cq.panel.admin.server.web.domain.vo.batch;

import lombok.Data;

import java.util.Map;

/**
 * 传输任务汇总统计VO
 *
 * @author cq
 */
@Data
public class TaskSummaryVO {

    /** 任务总数 */
    private Long totalCount;

    /** 就绪状态任务数(READY) */
    private Long readyCount;

    /** 运行中任务数(RUNNING) */
    private Long runningCount;

    /** 暂停状态任务数(PAUSED) */
    private Long pausedCount;

    /** 活跃任务数(运行中的任务) */
    private Long activeCount;

    /** 今日新增任务数 */
    private Long todayCount;

    /** 各状态数量分布 */
    private Map<String, Long> statusDistribution;
}
