package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.BatchTransferSubtask;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 批量传输子任务Mapper (Proxy端更新进度用)
 */
@Mapper
public interface BatchTransferSubtaskMapper {

    /**
     * 插入子任务
     */
    @Insert("""
            INSERT INTO batch_transfer_subtask (
                id, task_id, scan_batch_id, file_batch_id,
                source_agent_id, source_agent_name,
                target_agent_id, target_agent_name,
                source_path, target_path, file_name, file_size_bytes, file_last_modified,
                status, transfer_id,
                transferred_chunks, total_chunks, transferred_bytes, speed_bytes_per_sec,
                started_at, completed_at, duration_ms,
                error_code, error_message, error_stack_trace,
                retry_count, last_retry_at, next_retry_after,
                create_by, create_time, update_by, update_time, remark
            ) VALUES (
                #{id}, #{taskId}, #{scanBatchId}, #{fileBatchId},
                #{sourceAgentId}, #{sourceAgentName},
                #{targetAgentId}, #{targetAgentName},
                #{sourcePath}, #{targetPath}, #{fileName}, #{fileSizeBytes}, #{fileLastModified},
                #{status}, #{transferId},
                #{transferredChunks}, #{totalChunks}, #{transferredBytes}, #{speedBytesPerSec},
                #{startedAt}, #{completedAt}, #{durationMs},
                #{errorCode}, #{errorMessage}, #{errorStackTrace},
                #{retryCount}, #{lastRetryAt}, #{nextRetryAfter},
                #{createBy}, #{createTime}, #{updateBy}, #{updateTime}, #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BatchTransferSubtask subtask);

    /**
     * 根据实体更新子任务（只更新非空字段）
     */
    int updateByEntity(BatchTransferSubtask subtask);

    /**
     * 更新进度字段
     */
    @Update("""
            UPDATE batch_transfer_subtask
            SET transferred_chunks = #{transferredChunks},
                total_chunks = COALESCE(#{totalChunks}, total_chunks),
                transferred_bytes = #{transferredBytes},
                speed_bytes_per_sec = #{speedBytesPerSec},
                status = 'SENDING',
                started_at = COALESCE(started_at, NOW()),
                update_time = NOW()
            WHERE id = #{id}
            """)
    int updateProgress(
            @Param("id") Long id,
            @Param("transferredChunks") Integer transferredChunks,
            @Param("totalChunks") Integer totalChunks,
            @Param("transferredBytes") Long transferredBytes,
            @Param("speedBytesPerSec") Long speedBytesPerSec);

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
            @Param("durationMs") Long durationMs);

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
            @Param("errorMessage") String errorMessage);

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
            @Param("nextRetryAfter") Date nextRetryAfter);

    /**
     * 批量更新进度 (JDBC Batch)
     */
    int batchUpdateProgress(List<Map<String, Object>> progressList);
}
