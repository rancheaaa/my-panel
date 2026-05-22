package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.web.domain.vo.batch.SubtaskSummaryVO;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskSummaryVO;

/**
 * 批量传输统计服务接口
 *
 * @author cq
 */
public interface IBatchStatisticsService {

    /**
     * 获取传输明细文件汇总统计
     *
     * @return 传输明细文件汇总信息
     */
    SubtaskSummaryVO getSubtaskSummary();

    /**
     * 获取传输任务汇总统计
     *
     * @return 传输任务汇总信息
     */
    TaskSummaryVO getTaskSummary();
}
