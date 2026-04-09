package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.ArchDiagramTemplate;
import java.util.List;

/**
 * 架构图模板 服务层
 * 
 * @author cq
 */
public interface IArchDiagramTemplateService
{
    /**
     * 查询架构图模板集合
     * 
     * @param archDiagramTemplate 架构图模板信息
     * @return 架构图模板集合
     */
    List<ArchDiagramTemplate> selectArchDiagramTemplateList(ArchDiagramTemplate archDiagramTemplate);

    /**
     * 查询所有架构图模板
     * 
     * @return 架构图模板列表
     */
    List<ArchDiagramTemplate> selectArchDiagramTemplateAll();

    /**
     * 通过模板ID查询架构图模板信息
     * 
     * @param id 模板ID
     * @return 架构图模板对象信息
     */
    ArchDiagramTemplate selectArchDiagramTemplateById(Long id);

    /**
     * 新增架构图模板信息
     * 
     * @param archDiagramTemplate 架构图模板信息
     * @return 结果
     */
    int insertArchDiagramTemplate(ArchDiagramTemplate archDiagramTemplate);

    /**
     * 修改架构图模板信息
     * 
     * @param archDiagramTemplate 架构图模板信息
     * @return 结果
     */
    int updateArchDiagramTemplate(ArchDiagramTemplate archDiagramTemplate);

    /**
     * 删除架构图模板信息
     * 
     * @param id 模板ID
     * @return 结果
     */
    int deleteArchDiagramTemplateById(Long id);

    /**
     * 批量删除架构图模板信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteArchDiagramTemplateByIds(Long[] ids);

    /**
     * 使用模板
     * 
     * @param templateId 模板ID
     * @return 结果
     */
    int useTemplate(Long templateId);

    /**
     * 评分模板
     * 
     * @param templateId 模板ID
     * @param rating 评分
     * @return 结果
     */
    int rateTemplate(Long templateId, Double rating);
}