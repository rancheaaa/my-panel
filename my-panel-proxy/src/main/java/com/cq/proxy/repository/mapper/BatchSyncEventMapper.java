package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.BatchSyncEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
    @Select("""
        SELECT * FROM batch_sync_event
        WHERE status = 'PENDING'
          AND (next_retry_at IS NULL OR next_retry_at <= NOW())
          AND (expire_at IS NULL OR expire_at >= NOW())
        ORDER BY created_at ASC
        LIMIT #{limit}
        """)
    List<BatchSyncEvent> selectPendingEvents(@Param("limit") int limit);

    /**
     * 更新状态为PROCESSING
     */
    @Update("""
        UPDATE batch_sync_event
        SET status = 'PROCESSING',
            started_at = NOW(),
            update_time = NOW()
        WHERE id = #{id}
        """)
    int updateStatusToProcessing(BatchSyncEvent event);

    /**
     * 更新状态为COMPLETED
     */
    @Update("""
        UPDATE batch_sync_event
        SET status = 'COMPLETED',
            completed_at = NOW(),
            update_time = NOW()
        WHERE id = #{id}
        """)
    int updateStatusToCompleted(@Param("id") Long id);

    /**
     * 更新状态为FAILED
     */
    @Update("""
        UPDATE batch_sync_event
        SET status = 'FAILED',
            error_message = #{errorMessage},
            update_time = NOW()
        WHERE id = #{id}
        """)
    int updateStatusToFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);

    /**
     * 更新状态为PENDING（恢复卡住的事件）
     */
    @Update("""
        UPDATE batch_sync_event
        SET status = 'PENDING',
            started_at = NULL,
            update_time = NOW()
        WHERE id = #{id}
        """)
    int updateStatusToPending(@Param("id") Long id);

    /**
     * 更新重试信息（指数退避）
     */
    @Update("""
        UPDATE batch_sync_event
        SET retry_count = retry_count + 1,
            next_retry_at = #{nextRetryAt},
            error_message = #{errorMessage},
            status = 'PENDING',
            update_time = NOW()
        WHERE id = #{id}
        """)
    int updateRetry(@Param("id") Long id, @Param("nextRetryAt") Date nextRetryAt, @Param("errorMessage") String errorMessage);

    /**
     * 查询过期事件
     */
    @Select("""
        SELECT * FROM batch_sync_event
        WHERE status = 'PENDING'
          AND expire_at IS NOT NULL
          AND expire_at < #{now}
        """)
    List<BatchSyncEvent> selectExpiredEvents(@Param("now") Date now);

    /**
     * 查询卡住的PROCESSING事件
     */
    @Select("""
        SELECT * FROM batch_sync_event
        WHERE status = 'PROCESSING'
          AND started_at IS NOT NULL
          AND started_at < #{threshold}
        """)
    List<BatchSyncEvent> selectStuckProcessingEvents(@Param("threshold") Date threshold);
}
