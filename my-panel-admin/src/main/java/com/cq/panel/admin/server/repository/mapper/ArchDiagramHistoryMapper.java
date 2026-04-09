package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchDiagramHistory;
import java.util.List;

/**
 * 架构图版本历史 数据层
 * 
 * @author cq
 */
public interface ArchDiagramHistoryMapper
{
    /**
     * 查询架构图版本历史集合
     * 
     * @param archDiagramHistory 架构图版本历史信息
     * @return 架构图版本历史集合
     */
    public List<ArchDiagramHistory> selectArchDiagramHistoryList(ArchDiagramHistory archDiagramHistory);

    /**
     * 通过架构图ID查询版本历史列表
     * 
     * @param diagramId 架构图ID
     * @return 版本历史列表
     */
    public List<ArchDiagramHistory> selectHistoryByDiagramId(Long diagramId);

    /**
     * 通过历史ID查询版本历史信息
     * 
     * @param id 历史ID
     * @return 版本历史对象信息
     */
    public ArchDiagramHistory selectArchDiagramHistoryById(Long id);

    /**
     * 通过历史ID删除版本历史信息
     * 
     * @param id 历史ID
     * @return 结果
     */
    public int deleteArchDiagramHistoryById(Long id);

    /**
     * 批量删除版本历史信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteArchDiagramHistoryByIds(Long[] ids);

    /**
     * 修改版本历史信息
     * 
     * @param archDiagramHistory 版本历史信息
     * @return 结果
     */
    public int updateArchDiagramHistory(ArchDiagramHistory archDiagramHistory);

    /**
     * 新增版本历史信息
     * 
     * @param archDiagramHistory 版本历史信息
     * @return 结果
     */
    public int insertArchDiagramHistory(ArchDiagramHistory archDiagramHistory);

    /**
     * 查询架构图的当前版本
     * 
     * @param diagramId 架构图ID
     * @return 当前版本信息
     */
    public ArchDiagramHistory selectCurrentVersion(Long diagramId);

    /**
     * 更新架构图的当前版本
     * 
     * @param diagramId 架构图ID
     * @return 结果
     */
    public int updateCurrentVersion(Long diagramId);

    /**
     * 将指定历史版本设为当前版本
     * 
     * @param historyId 历史ID
     * @return 结果
     */
    public int updateHistoryToCurrent(Long historyId);
}