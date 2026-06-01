package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.BatchTransferSubtask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 批量传输子任务Mapper接口
 */
@Mapper
public interface BatchTransferSubtaskMapper {

    /**
     * 插入子任务
     */
    int insert(BatchTransferSubtask subtask);

    /**
     * 批量插入子任务
     */
    int batchInsert(@Param("list") List<BatchTransferSubtask> subtasks);

    /**
     * 根据任务ID和状态查询
     */
    List<BatchTransferSubtask> selectByTaskIdAndStatus(
        @Param("taskId") Long taskId,
        @Param("status") String status
    );

    /**
     * 分页查询子任务列表（支持条件过滤）
     */
    List<BatchTransferSubtask> selectPageList(
        @Param("taskId") Long taskId,
        @Param("status") String status,
        @Param("sourceFilePath") String sourceFilePath,
        @Param("targetFilePath") String targetFilePath,
        @Param("targetAgentId") String targetAgentId,
        @Param("sourceAgentId") String sourceAgentId,
        @Param("fileName") String fileName,
        @Param("scanBatchId") Long scanBatchId,
        @Param("fileBatchId") Long fileBatchId,
        @Param("sourceAgentName") String sourceAgentName,
        @Param("targetAgentName") String targetAgentName,
        @Param("offset") Integer offset,
        @Param("limit") Integer limit
    );

    /**
     * 查询子任务列表（按实体条件，不分页，用于导出）
     */
    List<BatchTransferSubtask> selectList(@Param("query") BatchTransferSubtask query);

    long countByCondition(
        @Param("taskId") Long taskId,
        @Param("status") String status,
        @Param("sourceFilePath") String sourceFilePath,
        @Param("targetFilePath") String targetFilePath,
        @Param("targetAgentId") String targetAgentId,
        @Param("sourceAgentId") String sourceAgentId,
        @Param("fileName") String fileName,
        @Param("scanBatchId") Long scanBatchId,
        @Param("fileBatchId") Long fileBatchId,
        @Param("sourceAgentName") String sourceAgentName,
        @Param("targetAgentName") String targetAgentName
    );

    /**
     * 更新进度（部分字段更新）
     */
    int updateProgress(
        @Param("id") Long id,
        @Param("transferredChunks") Integer chunks,
        @Param("transferredBytes") Long bytes,
        @Param("speedBytesPerSec") Long speedBytesPerSec
    );

    /**
     * 更新状态（支持多种状态变更场景）
     */
    int updateStatus(
        @Param("id") Long id,
        @Param("status") String status,
        @Param("errorCode") String errorCode,
        @Param("errorMessage") String errorMessage,
        @Param("errorStackTrace") String errorStackTrace,
        @Param("durationMs") Long durationMs,
        @Param("nextRetryAfter") java.util.Date nextRetryAfter
    );

    /**
     * 按任务ID统计各状态的子任务数量
     */
    List<Map<String, Object>> countByTaskIdGroupByStatus(@Param("taskId") Long taskId);

    /**
     * 统计所有子任务的汇总信息
     */
    Map<String, Object> countSummary();

    /**
     * 按状态分组统计子任务数量
     */
    List<Map<String, Object>> countGroupByStatus();

    /**
     * 按状态统计文件大小总和
     */
    Map<String, Object> sumFileSizeGroupByStatus();

    /**
     * 统计已完成任务的平均传输速度
     */
    Double avgSpeedForCompleted();

    /**
     * 统计今日新增的子任务数量
     */
    Long countToday();
}
