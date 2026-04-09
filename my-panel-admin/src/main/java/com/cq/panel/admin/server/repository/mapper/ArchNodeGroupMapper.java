package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchNodeGroup;
import java.util.List;

/**
 * 节点分组 数据层
 * 
 * @author cq
 */
public interface ArchNodeGroupMapper
{
    /**
     * 查询节点分组集合
     * 
     * @param archNodeGroup 节点分组信息
     * @return 节点分组集合
     */
    public List<ArchNodeGroup> selectArchNodeGroupList(ArchNodeGroup archNodeGroup);

    /**
     * 通过架构图ID查询分组列表
     * 
     * @param diagramId 架构图ID
     * @return 分组列表
     */
    public List<ArchNodeGroup> selectGroupsByDiagramId(Long diagramId);

    /**
     * 通过分组ID查询节点分组信息
     * 
     * @param id 分组ID
     * @return 节点分组对象信息
     */
    public ArchNodeGroup selectArchNodeGroupById(Long id);

    /**
     * 通过分组ID删除节点分组信息
     * 
     * @param id 分组ID
     * @return 结果
     */
    public int deleteArchNodeGroupById(Long id);

    /**
     * 批量删除节点分组信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteArchNodeGroupByIds(Long[] ids);

    /**
     * 修改节点分组信息
     * 
     * @param archNodeGroup 节点分组信息
     * @return 结果
     */
    public int updateArchNodeGroup(ArchNodeGroup archNodeGroup);

    /**
     * 新增节点分组信息
     * 
     * @param archNodeGroup 节点分组信息
     * @return 结果
     */
    public int insertArchNodeGroup(ArchNodeGroup archNodeGroup);
}