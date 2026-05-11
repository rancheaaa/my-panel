package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.BatchTransferSubtask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 批量传输子任务Mapper (Proxy端更新进度用)
 */
@Mapper
public interface BatchTransferSubtaskMapper {

    /**
     * 更新进度字段
     */
    @Update("""
        UPDATE batch_transfer_subtask
        SET transferred_chunks = #{transferredChunks},
            transferred_bytes = #{transferredBytes},
            speed_bytes_per_sec = #{speedBytesPerSec},
            update_time = NOW()
        WHERE id = #{id}
        """)
    int updateProgress(
        @Param("id") Long id,
        @Param("transferredChunks") Integer transferredChunks,
        @Param("transferredBytes") Long transferredBytes,
        @Param("speedBytesPerSec") Long speedBytesPerSec
    );

    /**
     * 更新状态为COMPLETED
     */
    @Update("""
        UPDATE batch_transfer_subtask
        SET status = 'COMPLETED',
            completed_at = NOW(),
            duration_ms = #{durationMs},
            update_time = NOW()
        WHERE id = #{id}
        """)
    int updateStatusCompleted(
        @Param("id") Long id,
        @Param("durationMs") Long durationMs
    );

    /**
     * 更新状态为FAILED
     */
    @Update("""
        UPDATE batch_transfer_subtask
        SET status = 'FAILED',
            error_code = #{errorCode},
            error_message = #{errorMessage},
            update_time = NOW()
        WHERE id = #{id}
        """)
    int updateStatusFailed(
        @Param("id") Long id,
        @Param("errorCode") String errorCode,
        @Param("errorMessage") String errorMessage
    );

    /**
     * 更新状态为RETRYING
     */
    @Update("""
        UPDATE batch_transfer_subtask
        SET status = 'RETRYING',
            retry_count = retry_count + 1,
            last_retry_at = NOW(),
            next_retry_after = #{nextRetryAfter},
            update_time = NOW()
        WHERE id = #{id}
        """)
    int updateStatusRetrying(
        @Param("id") Long id,
        @Param("nextRetryAfter") Date nextRetryAfter
    );

    /**
     * 批量更新进度 (JDBC Batch)
     */
    int batchUpdateProgress(List<Map<String, Object>> progressList);
}
