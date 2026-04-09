package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchNode;
import java.util.List;

/**
 * 节点 数据层
 * 
 * @author cq
 */
public interface ArchNodeMapper
{
    /**
     * 查询节点集合
     * 
     * @param archNode 节点信息
     * @return 节点集合
     */
    public List<ArchNode> selectArchNodeList(ArchNode archNode);

    /**
     * 通过架构图ID查询所有节点
     * 
     * @param diagramId 架构图ID
     * @return 节点列表
     */
    public List<ArchNode> selectNodesByDiagramId(Long diagramId);

    /**
     * 通过节点ID查询节点信息
     * 
     * @param id 节点ID
     * @return 节点对象信息
     */
    public ArchNode selectArchNodeById(Long id);

    /**
     * 通过节点ID删除节点信息
     * 
     * @param id 节点ID
     * @return 结果
     */
    public int deleteArchNodeById(Long id);

    /**
     * 批量删除节点信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteArchNodeByIds(Long[] ids);

    /**
     * 修改节点信息
     * 
     * @param archNode 节点信息
     * @return 结果
     */
    public int updateArchNode(ArchNode archNode);

    /**
     * 新增节点信息
     * 
     * @param archNode 节点信息
     * @return 结果
     */
    public int insertArchNode(ArchNode archNode);

    /**
     * 批量新增节点信息
     * 
     * @param nodeList 节点列表
     * @return 结果
     */
    public int batchInsertArchNode(List<ArchNode> nodeList);
}