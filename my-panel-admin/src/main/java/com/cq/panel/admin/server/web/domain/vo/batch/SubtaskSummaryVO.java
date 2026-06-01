package com.cq.panel.admin.server.web.domain.vo.batch;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 传输明细文件汇总统计VO
 *
 * @author cq
 */
@Data
public class SubtaskSummaryVO {

    /** 总文件数 */
    private Long totalCount;

    /** 总文件大小(字节) */
    private Long totalSizeBytes;

    /** 已完成文件数 */
    private Long completedCount;

    /** 已完成文件大小(字节) */
    private Long completedSizeBytes;

    /** 传输中文件数 */
    private Long sendingCount;

    /** 失败文件数 */
    private Long failedCount;

    /** 排队中文件数 */
    private Long queuedCount;

    /** 重试中文件数 */
    private Long retryingCount;

    /** 传输进度(百分比, 0-100) */
    private BigDecimal progressPercent;

    /** 平均传输速度(字节/秒) */
    private BigDecimal avgSpeedBytesPerSec;

    /** 各状态数量分布 */
    private Map<String, Long> statusDistribution;

    /** 今日新增文件数 */
    private Long todayCount;
}
