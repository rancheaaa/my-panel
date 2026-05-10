package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.BatchSyncEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 批量同步事件Mapper接口
 * 
 * @author cq
 */
@Mapper
public interface BatchSyncEventMapper {

    /**
     * 插入事件
     */
    int insertEvent(BatchSyncEvent event);

    /**
     * 查询待处理事件（FOR UPDATE SKIP LOCKED，用于并发轮询）
     */
    List<BatchSyncEvent> selectPendingEventsForUpdate(@Param("limit") int limit);

    /**
     * 更新状态为处理中
     */
    int updateStatusToProcessing(@Param("id") Long id);

    /**
     * 更新状态为已完成
     */
    int updateStatusToCompleted(@Param("id") Long id);

    /**
     * 查询过期事件（用于清理）
     */
    List<BatchSyncEvent> selectExpiredEvents();
}
