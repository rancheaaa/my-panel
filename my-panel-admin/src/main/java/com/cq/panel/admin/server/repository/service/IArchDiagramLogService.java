package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.ArchDiagramLog;
import java.util.List;

/**
 * 架构图操作日志 服务层
 * 
 * @author cq
 */
public interface IArchDiagramLogService
{
    /**
     * 查询架构图操作日志集合
     * 
     * @param archDiagramLog 架构图操作日志信息
     * @return 架构图操作日志集合
     */
    List<ArchDiagramLog> selectArchDiagramLogList(ArchDiagramLog archDiagramLog);

    /**
     * 通过架构图ID查询操作日志列表
     * 
     * @param diagramId 架构图ID
     * @return 操作日志列表
     */
    List<ArchDiagramLog> selectLogsByDiagramId(Long diagramId);

    /**
     * 通过日志ID查询操作日志信息
     * 
     * @param id 日志ID
     * @return 操作日志对象信息
     */
    ArchDiagramLog selectArchDiagramLogById(Long id);

    /**
     * 新增操作日志信息
     * 
     * @param archDiagramLog 操作日志信息
     * @return 结果
     */
    int insertArchDiagramLog(ArchDiagramLog archDiagramLog);

    /**
     * 批量新增操作日志信息
     * 
     * @param archDiagramLogs 操作日志信息列表
     * @return 结果
     */
    int batchInsertArchDiagramLog(List<ArchDiagramLog> archDiagramLogs);

    /**
     * 删除操作日志信息
     * 
     * @param id 日志ID
     * @return 结果
     */
    int deleteArchDiagramLogById(Long id);

    /**
     * 批量删除操作日志信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteArchDiagramLogByIds(Long[] ids);

    /**
     * 清空指定架构图的操作日志
     * 
     * @param diagramId 架构图ID
     * @return 结果
     */
    int clearLogsByDiagramId(Long diagramId);
}