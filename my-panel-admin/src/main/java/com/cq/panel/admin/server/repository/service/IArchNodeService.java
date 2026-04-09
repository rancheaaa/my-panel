package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.ArchNode;
import java.util.List;

/**
 * 节点信息 服务层
 * 
 * @author cq
 */
public interface IArchNodeService
{
    /**
     * 查询节点集合
     * 
     * @param archNode 节点信息
     * @return 节点集合
     */
    List<ArchNode> selectArchNodeList(ArchNode archNode);

    /**
     * 通过架构图ID查询所有节点
     * 
     * @param diagramId 架构图ID
     * @return 节点列表
     */
    List<ArchNode> selectNodesByDiagramId(Long diagramId);

    /**
     * 通过节点ID查询节点信息
     * 
     * @param id 节点ID
     * @return 节点对象信息
     */
    ArchNode selectArchNodeById(Long id);

    /**
     * 新增节点信息
     * 
     * @param archNode 节点信息
     * @return 结果
     */
    int insertArchNode(ArchNode archNode);

    /**
     * 批量新增节点信息
     * 
     * @param nodeList 节点列表
     * @return 结果
     */
    int batchInsertArchNode(List<ArchNode> nodeList);

    /**
     * 修改节点信息
     * 
     * @param archNode 节点信息
     * @return 结果
     */
    int updateArchNode(ArchNode archNode);

    /**
     * 批量修改节点位置信息
     * 
     * @param nodeList 节点列表
     * @return 结果
     */
    int batchUpdateArchNodePosition(List<ArchNode> nodeList);

    /**
     * 删除节点信息
     * 
     * @param id 节点ID
     * @return 结果
     */
    int deleteArchNodeById(Long id);

    /**
     * 批量删除节点信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteArchNodeByIds(Long[] ids);
}