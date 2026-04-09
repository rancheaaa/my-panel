package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchDiagramTemplate;
import java.util.List;

/**
 * 架构图模板 数据层
 * 
 * @author cq
 */
public interface ArchDiagramTemplateMapper
{
    /**
     * 查询架构图模板集合
     * 
     * @param archDiagramTemplate 架构图模板信息
     * @return 架构图模板集合
     */
    public List<ArchDiagramTemplate> selectArchDiagramTemplateList(ArchDiagramTemplate archDiagramTemplate);

    /**
     * 查询所有架构图模板
     * 
     * @return 架构图模板列表
     */
    public List<ArchDiagramTemplate> selectArchDiagramTemplateAll();

    /**
     * 通过模板ID查询架构图模板信息
     * 
     * @param id 模板ID
     * @return 架构图模板对象信息
     */
    public ArchDiagramTemplate selectArchDiagramTemplateById(Long id);

    /**
     * 通过模板ID删除架构图模板信息
     * 
     * @param id 模板ID
     * @return 结果
     */
    public int deleteArchDiagramTemplateById(Long id);

    /**
     * 批量删除架构图模板信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteArchDiagramTemplateByIds(Long[] ids);

    /**
     * 修改架构图模板信息
     * 
     * @param archDiagramTemplate 架构图模板信息
     * @return 结果
     */
    public int updateArchDiagramTemplate(ArchDiagramTemplate archDiagramTemplate);

    /**
     * 新增架构图模板信息
     * 
     * @param archDiagramTemplate 架构图模板信息
     * @return 结果
     */
    public int insertArchDiagramTemplate(ArchDiagramTemplate archDiagramTemplate);

    /**
     * 增加模板使用次数
     * 
     * @param id 模板ID
     * @return 结果
     */
    public int incrementUseCount(Long id);

    /**
     * 更新模板评分
     * 
     * @param id 模板ID
     * @param rating 评分
     * @return 结果
     */
    public int updateRating(Long id, Double rating);
}