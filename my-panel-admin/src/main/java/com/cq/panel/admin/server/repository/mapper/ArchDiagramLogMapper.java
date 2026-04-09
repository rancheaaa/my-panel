package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchDiagramLog;
import java.util.List;

/**
 * 架构图操作日志 数据层
 * 
 * @author cq
 */
public interface ArchDiagramLogMapper
{
    /**
     * 查询架构图操作日志集合
     * 
     * @param archDiagramLog 架构图操作日志信息
     * @return 架构图操作日志集合
     */
    public List<ArchDiagramLog> selectArchDiagramLogList(ArchDiagramLog archDiagramLog);

    /**
     * 通过架构图ID查询操作日志列表
     * 
     * @param diagramId 架构图ID
     * @return 操作日志列表
     */
    public List<ArchDiagramLog> selectLogsByDiagramId(Long diagramId);

    /**
     * 通过日志ID查询操作日志信息
     * 
     * @param id 日志ID
     * @return 操作日志对象信息
     */
    public ArchDiagramLog selectArchDiagramLogById(Long id);

    /**
     * 通过日志ID删除操作日志信息
     * 
     * @param id 日志ID
     * @return 结果
     */
    public int deleteArchDiagramLogById(Long id);

    /**
     * 批量删除操作日志信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteArchDiagramLogByIds(Long[] ids);

    /**
     * 清空指定架构图的操作日志
     * 
     * @param diagramId 架构图ID
     * @return 结果
     */
    public int clearLogsByDiagramId(Long diagramId);

    /**
     * 新增操作日志信息
     * 
     * @param archDiagramLog 操作日志信息
     * @return 结果
     */
    public int insertArchDiagramLog(ArchDiagramLog archDiagramLog);

    /**
     * 批量新增操作日志信息
     * 
     * @param archDiagramLogs 操作日志信息列表
     * @return 结果
     */
    public int batchInsertArchDiagramLog(List<ArchDiagramLog> archDiagramLogs);
}