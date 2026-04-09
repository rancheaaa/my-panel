package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.ArchDiagram;
import java.util.List;

/**
 * 架构图信息 服务层
 * 
 * @author cq
 */
public interface IArchDiagramService
{
    /**
     * 查询架构图集合
     * 
     * @param archDiagram 架构图信息
     * @return 架构图集合
     */
    List<ArchDiagram> selectArchDiagramList(ArchDiagram archDiagram);

    /**
     * 查询所有架构图
     * 
     * @return 架构图列表
     */
    List<ArchDiagram> selectArchDiagramAll();

    /**
     * 通过架构图ID查询架构图信息
     * 
     * @param id 架构图ID
     * @return 架构图对象信息
     */
    ArchDiagram selectArchDiagramById(Long id);

    /**
     * 新增架构图信息
     * 
     * @param archDiagram 架构图信息
     * @return 结果
     */
    int insertArchDiagram(ArchDiagram archDiagram);

    /**
     * 修改架构图信息
     * 
     * @param archDiagram 架构图信息
     * @return 结果
     */
    int updateArchDiagram(ArchDiagram archDiagram);

    /**
     * 发布架构图
     * 
     * @param archDiagram 架构图信息
     * @return 结果
     */
    int publishArchDiagram(ArchDiagram archDiagram);

    /**
     * 删除架构图信息
     * 
     * @param id 架构图ID
     * @return 结果
     */
    int deleteArchDiagramById(Long id);

    /**
     * 批量删除架构图信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteArchDiagramByIds(Long[] ids);
}