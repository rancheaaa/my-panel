package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 批量传输任务Mapper接口
 *
 * @author cq
 */
@Mapper
public interface BatchTransferTaskMapper {

    /**
     * 插入任务
     */
    int insert(BatchTransferTask task);

    /**
     * 更新任务
     */
    int updateById(BatchTransferTask task);

    /**
     * 根据ID查询
     */
    BatchTransferTask selectById(@Param("id") Long id);

    /**
     * 查询列表
     */
    List<BatchTransferTask> selectList(BatchTransferTask query);

    /**
     * 分页查询列表
     */
    List<BatchTransferTask> selectPageList(@Param("query") BatchTransferTask query,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    /**
     * 逻辑删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 统计所有任务的汇总信息
     */
    Map<String, Object> countSummary();

    /**
     * 按状态分组统计任务数量
     */
    List<Map<String, Object>> countGroupByStatus();

    /**
     * 统计今日新增的任务数量
     */
    Long countToday();
}
