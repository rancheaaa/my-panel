package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.ArchDiagramHistory;
import java.util.List;

/**
 * 架构图版本历史 服务层
 * 
 * @author cq
 */
public interface IArchDiagramHistoryService
{
    /**
     * 查询架构图版本历史集合
     * 
     * @param archDiagramHistory 架构图版本历史信息
     * @return 架构图版本历史集合
     */
    List<ArchDiagramHistory> selectArchDiagramHistoryList(ArchDiagramHistory archDiagramHistory);

    /**
     * 通过架构图ID查询版本历史列表
     * 
     * @param diagramId 架构图ID
     * @return 版本历史列表
     */
    List<ArchDiagramHistory> selectHistoryByDiagramId(Long diagramId);

    /**
     * 通过历史ID查询版本历史信息
     * 
     * @param id 历史ID
     * @return 版本历史对象信息
     */
    ArchDiagramHistory selectArchDiagramHistoryById(Long id);

    /**
     * 查询架构图的当前版本
     * 
     * @param diagramId 架构图ID
     * @return 当前版本信息
     */
    ArchDiagramHistory selectCurrentVersion(Long diagramId);

    /**
     * 新增版本历史信息
     * 
     * @param archDiagramHistory 版本历史信息
     * @return 结果
     */
    int insertArchDiagramHistory(ArchDiagramHistory archDiagramHistory);

    /**
     * 修改版本历史信息
     * 
     * @param archDiagramHistory 版本历史信息
     * @return 结果
     */
    int updateArchDiagramHistory(ArchDiagramHistory archDiagramHistory);

    /**
     * 删除版本历史信息
     * 
     * @param id 历史ID
     * @return 结果
     */
    int deleteArchDiagramHistoryById(Long id);

    /**
     * 批量删除版本历史信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteArchDiagramHistoryByIds(Long[] ids);

    /**
     * 创建版本快照
     * 
     * @param diagramId 架构图ID
     * @param version 版本号
     * @param versionName 版本名称
     * @param changeSummary 变更摘要
     * @return 结果
     */
    int createSnapshot(Long diagramId, String version, String versionName, String changeSummary);

    /**
     * 恢复到指定版本
     * 
     * @param historyId 历史ID
     * @return 结果
     */
    int restoreVersion(Long historyId);
}