package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.BatchTransferSubtask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 批量传输子任务Mapper接口
 * 
 * @author cq
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
     * 更新进度（部分字段更新）
     */
    int updateProgress(
        @Param("id") Long id, 
        @Param("transferredChunks") Integer chunks, 
        @Param("transferredBytes") Long bytes
    );

    /**
     * 更新状态
     */
    int updateStatus(
        @Param("id") Long id, 
        @Param("status") String status, 
        @Param("errorMessage") String error
    );
}
