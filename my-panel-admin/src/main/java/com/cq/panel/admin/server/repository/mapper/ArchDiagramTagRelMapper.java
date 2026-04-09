package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchDiagramTagRel;
import java.util.List;

/**
 * 架构图标签关联 数据层
 * 
 * @author cq
 */
public interface ArchDiagramTagRelMapper
{
    /**
     * 查询架构图标签关联集合
     * 
     * @param archDiagramTagRel 架构图标签关联信息
     * @return 架构图标签关联集合
     */
    public List<ArchDiagramTagRel> selectArchDiagramTagRelList(ArchDiagramTagRel archDiagramTagRel);

    /**
     * 通过架构图ID查询关联的标签ID列表
     * 
     * @param diagramId 架构图ID
     * @return 标签ID列表
     */
    public List<Long> selectTagIdsByDiagramId(Long diagramId);

    /**
     * 通过标签ID查询关联的架构图ID列表
     * 
     * @param tagId 标签ID
     * @return 架构图ID列表
     */
    public List<Long> selectDiagramIdsByTagId(Long tagId);

    /**
     * 通过关联ID删除架构图标签关联信息
     * 
     * @param id 关联ID
     * @return 结果
     */
    public int deleteArchDiagramTagRelById(Long id);

    /**
     * 批量删除架构图标签关联信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteArchDiagramTagRelByIds(Long[] ids);

    /**
     * 通过架构图ID删除所有关联
     * 
     * @param diagramId 架构图ID
     * @return 结果
     */
    public int deleteArchDiagramTagRelByDiagramId(Long diagramId);

    /**
     * 通过标签ID删除所有关联
     * 
     * @param tagId 标签ID
     * @return 结果
     */
    public int deleteArchDiagramTagRelByTagId(Long tagId);

    /**
     * 新增架构图标签关联信息
     * 
     * @param archDiagramTagRel 架构图标签关联信息
     * @return 结果
     */
    public int insertArchDiagramTagRel(ArchDiagramTagRel archDiagramTagRel);

    /**
     * 批量新增架构图标签关联信息
     * 
     * @param archDiagramTagRels 架构图标签关联信息列表
     * @return 结果
     */
    public int batchInsertArchDiagramTagRel(List<ArchDiagramTagRel> archDiagramTagRels);
}