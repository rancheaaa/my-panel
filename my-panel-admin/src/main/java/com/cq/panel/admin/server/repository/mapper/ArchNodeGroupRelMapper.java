package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchNodeGroupRel;
import java.util.List;

/**
 * 节点分组关联 数据层
 * 
 * @author cq
 */
public interface ArchNodeGroupRelMapper
{
    /**
     * 查询节点分组关联集合
     * 
     * @param archNodeGroupRel 节点分组关联信息
     * @return 节点分组关联集合
     */
    public List<ArchNodeGroupRel> selectArchNodeGroupRelList(ArchNodeGroupRel archNodeGroupRel);

    /**
     * 通过分组ID查询关联的节点ID列表
     * 
     * @param groupId 分组ID
     * @return 节点ID列表
     */
    public List<Long> selectNodeIdsByGroupId(Long groupId);

    /**
     * 通过节点ID查询关联的分组ID列表
     * 
     * @param nodeId 节点ID
     * @return 分组ID列表
     */
    public List<Long> selectGroupIdsByNodeId(Long nodeId);

    /**
     * 通过关联ID删除节点分组关联信息
     * 
     * @param id 关联ID
     * @return 结果
     */
    public int deleteArchNodeGroupRelById(Long id);

    /**
     * 批量删除节点分组关联信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteArchNodeGroupRelByIds(Long[] ids);

    /**
     * 通过分组ID删除所有关联
     * 
     * @param groupId 分组ID
     * @return 结果
     */
    public int deleteArchNodeGroupRelByGroupId(Long groupId);

    /**
     * 通过节点ID删除所有关联
     * 
     * @param nodeId 节点ID
     * @return 结果
     */
    public int deleteArchNodeGroupRelByNodeId(Long nodeId);

    /**
     * 新增节点分组关联信息
     * 
     * @param archNodeGroupRel 节点分组关联信息
     * @return 结果
     */
    public int insertArchNodeGroupRel(ArchNodeGroupRel archNodeGroupRel);

    /**
     * 批量新增节点分组关联信息
     * 
     * @param archNodeGroupRels 节点分组关联信息列表
     * @return 结果
     */
    public int batchInsertArchNodeGroupRel(List<ArchNodeGroupRel> archNodeGroupRels);
}