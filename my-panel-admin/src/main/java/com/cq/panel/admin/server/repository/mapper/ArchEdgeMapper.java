package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchEdge;
import java.util.List;

/**
 * 边缘 数据层
 * 
 * @author cq
 */
public interface ArchEdgeMapper
{
    /**
     * 查询边缘集合
     * 
     * @param archEdge 边缘信息
     * @return 边缘集合
     */
    public List<ArchEdge> selectArchEdgeList(ArchEdge archEdge);

    /**
     * 通过架构图ID查询所有边缘
     * 
     * @param diagramId 架构图ID
     * @return 边缘列表
     */
    public List<ArchEdge> selectEdgesByDiagramId(Long diagramId);

    /**
     * 通过边缘ID查询边缘信息
     * 
     * @param id 边缘ID
     * @return 边缘对象信息
     */
    public ArchEdge selectArchEdgeById(Long id);

    /**
     * 通过边缘ID删除边缘信息
     * 
     * @param id 边缘ID
     * @return 结果
     */
    public int deleteArchEdgeById(Long id);

    /**
     * 批量删除边缘信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteArchEdgeByIds(Long[] ids);

    /**
     * 修改边缘信息
     * 
     * @param archEdge 边缘信息
     * @return 结果
     */
    public int updateArchEdge(ArchEdge archEdge);

    /**
     * 新增边缘信息
     * 
     * @param archEdge 边缘信息
     * @return 结果
     */
    public int insertArchEdge(ArchEdge archEdge);

    /**
     * 批量新增边缘信息
     * 
     * @param edgeList 边缘列表
     * @return 结果
     */
    public int batchInsertArchEdge(List<ArchEdge> edgeList);
}