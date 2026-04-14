package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchDiagram;
import java.util.List;

/**
 * 架构图 数据层
 * 
 * @author cq
 */
public interface ArchDiagramMapper
{
    /**
     * 查询架构图集合
     * 
     * @param archDiagram 架构图信息
     * @return 架构图集合
     */
    public List<ArchDiagram> selectArchDiagramList(ArchDiagram archDiagram);

    /**
     * 查询所有架构图
     * 
     * @return 架构图列表
     */
    public List<ArchDiagram> selectArchDiagramAll();

    /**
     * 通过架构图ID查询架构图信息
     * 
     * @param id 架构图ID
     * @return 架构图对象信息
     */
    public ArchDiagram selectArchDiagramById(Long id);

    /**
     * 通过架构图ID删除架构图信息
     * 
     * @param id 架构图ID
     * @return 结果
     */
    public int deleteArchDiagramById(Long id);

    /**
     * 批量删除架构图信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteArchDiagramByIds(Long[] ids);

    /**
     * 修改架构图信息
     * 
     * @param archDiagram 架构图信息
     * @return 结果
     */
    public int updateArchDiagram(ArchDiagram archDiagram);

    /**
     * 新增架构图信息
     * 
     * @param archDiagram 架构图信息
     * @return 结果
     */
    public int insertArchDiagram(ArchDiagram archDiagram);

    /**
     * 发布架构图
     * 
     * @param archDiagram 架构图信息
     * @return 结果
     */
    public int publishArchDiagram(ArchDiagram archDiagram);
}