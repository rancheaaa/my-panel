package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.ArchNodeGroup;
import java.util.List;

/**
 * 节点分组 服务层
 * 
 * @author cq
 */
public interface IArchNodeGroupService
{
    /**
     * 查询节点分组集合
     * 
     * @param archNodeGroup 节点分组信息
     * @return 节点分组集合
     */
    List<ArchNodeGroup> selectArchNodeGroupList(ArchNodeGroup archNodeGroup);

    /**
     * 通过架构图ID查询分组列表
     * 
     * @param diagramId 架构图ID
     * @return 分组列表
     */
    List<ArchNodeGroup> selectGroupsByDiagramId(Long diagramId);

    /**
     * 通过分组ID查询节点分组信息
     * 
     * @param id 分组ID
     * @return 节点分组对象信息
     */
    ArchNodeGroup selectArchNodeGroupById(Long id);

    /**
     * 新增节点分组信息
     * 
     * @param archNodeGroup 节点分组信息
     * @return 结果
     */
    int insertArchNodeGroup(ArchNodeGroup archNodeGroup);

    /**
     * 修改节点分组信息
     * 
     * @param archNodeGroup 节点分组信息
     * @return 结果
     */
    int updateArchNodeGroup(ArchNodeGroup archNodeGroup);

    /**
     * 删除节点分组信息
     * 
     * @param id 分组ID
     * @return 结果
     */
    int deleteArchNodeGroupById(Long id);

    /**
     * 批量删除节点分组信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteArchNodeGroupByIds(Long[] ids);
}