package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.ArchDiagramTagRel;
import java.util.List;

/**
 * 架构图标签关联 服务层
 * 
 * @author cq
 */
public interface IArchDiagramTagRelService
{
    /**
     * 查询架构图标签关联集合
     * 
     * @param archDiagramTagRel 架构图标签关联信息
     * @return 架构图标签关联集合
     */
    List<ArchDiagramTagRel> selectArchDiagramTagRelList(ArchDiagramTagRel archDiagramTagRel);

    /**
     * 通过架构图ID查询关联的标签ID列表
     * 
     * @param diagramId 架构图ID
     * @return 标签ID列表
     */
    List<Long> selectTagIdsByDiagramId(Long diagramId);

    /**
     * 通过标签ID查询关联的架构图ID列表
     * 
     * @param tagId 标签ID
     * @return 架构图ID列表
     */
    List<Long> selectDiagramIdsByTagId(Long tagId);

    /**
     * 通过节点ID查询关联的标签ID列表
     * 
     * @param nodeId 节点ID
     * @return 标签ID列表
     */
    List<Long> selectTagIdsByNodeId(Long nodeId);

    /**
     * 新增架构图标签关联信息
     * 
     * @param archDiagramTagRel 架构图标签关联信息
     * @return 结果
     */
    int insertArchDiagramTagRel(ArchDiagramTagRel archDiagramTagRel);

    /**
     * 批量新增架构图标签关联信息
     * 
     * @param archDiagramTagRels 架构图标签关联信息列表
     * @return 结果
     */
    int batchInsertArchDiagramTagRel(List<ArchDiagramTagRel> archDiagramTagRels);

    /**
     * 删除架构图标签关联信息
     * 
     * @param id 关联ID
     * @return 结果
     */
    int deleteArchDiagramTagRelById(Long id);

    /**
     * 批量删除架构图标签关联信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteArchDiagramTagRelByIds(Long[] ids);

    /**
     * 通过架构图ID删除所有关联
     * 
     * @param diagramId 架构图ID
     * @return 结果
     */
    int deleteArchDiagramTagRelByDiagramId(Long diagramId);

    /**
     * 通过标签ID删除所有关联
     * 
     * @param tagId 标签ID
     * @return 结果
     */
    int deleteArchDiagramTagRelByTagId(Long tagId);

    /**
     * 通过节点ID删除所有关联
     * 
     * @param nodeId 节点ID
     * @return 结果
     */
    int deleteArchDiagramTagRelByNodeId(Long nodeId);
}