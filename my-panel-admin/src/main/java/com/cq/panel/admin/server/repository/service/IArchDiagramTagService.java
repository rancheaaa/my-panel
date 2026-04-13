package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.ArchDiagramTag;
import java.util.List;

/**
 * 架构图标签 服务层
 * 
 * @author cq
 */
public interface IArchDiagramTagService
{
    /**
     * 查询架构图标签集合
     * 
     * @param archDiagramTag 架构图标签信息
     * @return 架构图标签集合
     */
    List<ArchDiagramTag> selectArchDiagramTagList(ArchDiagramTag archDiagramTag);

    /**
     * 查询所有架构图标签
     * 
     * @return 架构图标签列表
     */
    List<ArchDiagramTag> selectArchDiagramTagAll();

    /**
     * 通过标签ID查询架构图标签信息
     * 
     * @param id 标签ID
     * @return 架构图标签对象信息
     */
    ArchDiagramTag selectArchDiagramTagById(Long id);

    /**
     * 通过标签名称查询架构图标签信息
     * 
     * @param tagName 标签名称
     * @return 架构图标签对象信息
     */
    ArchDiagramTag selectArchDiagramTagByName(String tagName);

    /**
     * 新增架构图标签信息
     * 
     * @param archDiagramTag 架构图标签信息
     * @return 结果
     */
    int insertArchDiagramTag(ArchDiagramTag archDiagramTag);

    /**
     * 修改架构图标签信息
     * 
     * @param archDiagramTag 架构图标签信息
     * @return 结果
     */
    int updateArchDiagramTag(ArchDiagramTag archDiagramTag);

    /**
     * 删除架构图标签信息
     * 
     * @param id 标签ID
     * @return 结果
     */
    int deleteArchDiagramTagById(Long id);

    /**
     * 批量删除架构图标签信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteArchDiagramTagByIds(Long[] ids);



    /**
     * 校验标签名称是否唯一
     * 
     * @param archDiagramTag 架构图标签信息
     * @return 结果
     */
    boolean checkTagNameUnique(ArchDiagramTag archDiagramTag);
}