package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchDiagramTag;
import java.util.List;

/**
 * 架构图标签 数据层
 * 
 * @author cq
 */
public interface ArchDiagramTagMapper
{
    /**
     * 查询架构图标签集合
     * 
     * @param archDiagramTag 架构图标签信息
     * @return 架构图标签集合
     */
    public List<ArchDiagramTag> selectArchDiagramTagList(ArchDiagramTag archDiagramTag);

    /**
     * 查询所有架构图标签
     * 
     * @return 架构图标签列表
     */
    public List<ArchDiagramTag> selectArchDiagramTagAll();

    /**
     * 通过标签ID查询架构图标签信息
     * 
     * @param id 标签ID
     * @return 架构图标签对象信息
     */
    public ArchDiagramTag selectArchDiagramTagById(Long id);

    /**
     * 通过标签名称查询架构图标签信息
     * 
     * @param tagName 标签名称
     * @return 架构图标签对象信息
     */
    public ArchDiagramTag selectArchDiagramTagByName(String tagName);

    /**
     * 通过标签ID删除架构图标签信息
     * 
     * @param id 标签ID
     * @return 结果
     */
    public int deleteArchDiagramTagById(Long id);

    /**
     * 批量删除架构图标签信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteArchDiagramTagByIds(Long[] ids);

    /**
     * 修改架构图标签信息
     * 
     * @param archDiagramTag 架构图标签信息
     * @return 结果
     */
    public int updateArchDiagramTag(ArchDiagramTag archDiagramTag);

    /**
     * 新增架构图标签信息
     * 
     * @param archDiagramTag 架构图标签信息
     * @return 结果
     */
    public int insertArchDiagramTag(ArchDiagramTag archDiagramTag);

    /**
     * 增加标签使用次数
     * 
     * @param id 标签ID
     * @return 结果
     */
    public int incrementUseCount(Long id);

    /**
     * 校验标签名称是否唯一
     * 
     * @param archDiagramTag 架构图标签信息
     * @return 结果
     */
    public ArchDiagramTag checkTagNameUnique(ArchDiagramTag archDiagramTag);
}