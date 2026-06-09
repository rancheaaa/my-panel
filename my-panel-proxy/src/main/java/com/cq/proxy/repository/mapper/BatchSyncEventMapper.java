package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.BatchSyncEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 批量同步事件Mapper (Proxy端消费事件用)
 */
@Mapper
public interface BatchSyncEventMapper {

    /**
     * 查询待处理的事件
     * 排除已过期的事件（过期事件由cleanupExpiredEvents处理）
     */
    List<BatchSyncEvent> selectPendingEvents(@Param("limit") int limit);

    /**
     * 更新状态为PROCESSING
     */
    int updateStatusToProcessing(BatchSyncEvent event);

    /**
     * 更新状态为COMPLETED
     */
    int updateStatusToCompleted(@Param("id") Long id);

    /**
     * 更新状态为FAILED
     */
    int updateStatusToFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);

    /**
     * 更新状态为PENDING（恢复卡住的事件）
     */
    int updateStatusToPending(@Param("id") Long id);

    /**
     * 更新重试信息（指数退避）
     */
    int updateRetry(@Param("id") Long id, @Param("nextRetryAt") Date nextRetryAt, @Param("errorMessage") String errorMessage);

    /**
     * 查询过期事件
     */
    List<BatchSyncEvent> selectExpiredEvents(@Param("now") Date now);

    /**
     * 查询卡住的PROCESSING事件
     */
    List<BatchSyncEvent> selectStuckProcessingEvents(@Param("threshold") Date threshold);
}
