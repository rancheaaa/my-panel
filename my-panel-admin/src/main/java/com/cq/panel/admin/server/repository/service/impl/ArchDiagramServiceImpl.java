package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import com.cq.panel.admin.server.common.utils.MyDateUtils;
import com.cq.panel.admin.server.common.utils.SecurityUtils;
import com.cq.panel.admin.server.repository.domain.ArchEdge;
import com.cq.panel.admin.server.repository.domain.ArchNode;
import com.cq.panel.admin.server.repository.service.IArchEdgeService;
import com.cq.panel.admin.server.repository.service.IArchNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cq.panel.admin.server.repository.mapper.ArchDiagramMapper;
import com.cq.panel.admin.server.repository.domain.ArchDiagram;
import com.cq.panel.admin.server.repository.service.IArchDiagramService;

/**
 * 架构图Service业务层处理
 * 
 * @author cq
 */
@Service
public class ArchDiagramServiceImpl implements IArchDiagramService 
{
    @Autowired
    private ArchDiagramMapper archDiagramMapper;

    @Autowired
    private IArchNodeService archNodeService;

    @Autowired
    private IArchEdgeService archEdgeService;

    /**
     * 查询架构图
     * 
     * @param id 架构图主键
     * @return 架构图
     */
    @Override
    public ArchDiagram selectArchDiagramById(Long id)
    {
        return archDiagramMapper.selectArchDiagramById(id);
    }

    /**
     * 查询架构图列表
     * 
     * @param archDiagram 架构图
     * @return 架构图
     */
    @Override
    public List<ArchDiagram> selectArchDiagramList(ArchDiagram archDiagram)
    {
        return archDiagramMapper.selectArchDiagramList(archDiagram);
    }

    /**
     * 查询所有架构图
     * 
     * @return 架构图列表
     */
    @Override
    public List<ArchDiagram> selectArchDiagramAll()
    {
        return archDiagramMapper.selectArchDiagramAll();
    }

    /**
     * 新增架构图
     * 
     * @param archDiagram 架构图
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertArchDiagram(ArchDiagram archDiagram)
    {
        archDiagram.setCreateBy(SecurityUtils.getUsername());
        archDiagram.setCreateTime(MyDateUtils.getNowDate());
        archDiagram.setUpdateBy(SecurityUtils.getUsername());
        archDiagram.setUpdateTime(MyDateUtils.getNowDate());
        archDiagram.setStatus("0");
        archDiagram.setIsPublished("0");
        archDiagram.setDelFlag("0");
        return archDiagramMapper.insertArchDiagram(archDiagram);
    }

    /**
     * 修改架构图
     * 
     * @param archDiagram 架构图
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateArchDiagram(ArchDiagram archDiagram)
    {
        archDiagram.setUpdateBy(SecurityUtils.getUsername());
        archDiagram.setUpdateTime(MyDateUtils.getNowDate());
        return archDiagramMapper.updateArchDiagram(archDiagram);
    }

    /**
     * 发布架构图
     * 
     * @param archDiagram 架构图
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publishArchDiagram(ArchDiagram archDiagram)
    {
        archDiagram.setUpdateBy(SecurityUtils.getUsername());
        archDiagram.setUpdateTime(MyDateUtils.getNowDate());
        archDiagram.setPublishedAt(MyDateUtils.getNowDate());
        return archDiagramMapper.publishArchDiagram(archDiagram);
    }

    /**
     * 批量删除架构图
     * 
     * @param ids 需要删除的架构图主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteArchDiagramByIds(Long[] ids)
    {
        for (Long id : ids)
        {
            deleteArchDiagramById(id);
        }
        return ids.length;
    }

    /**
     * 删除架构图信息
     * 
     * @param id 架构图主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteArchDiagramById(Long id)
    {
        archDiagramMapper.deleteArchDiagramById(id);
        ArchNode queryNode = new ArchNode();
        queryNode.setDiagramId(id);
        List<ArchNode> nodes = archNodeService.selectArchNodeList(queryNode);
        if (nodes != null && !nodes.isEmpty())
        {
            Long[] nodeIds = nodes.stream().map(ArchNode::getId).toArray(Long[]::new);
            archNodeService.deleteArchNodeByIds(nodeIds);
        }
        ArchEdge queryEdge = new ArchEdge();
        queryEdge.setDiagramId(id);
        List<ArchEdge> edges = archEdgeService.selectArchEdgeList(queryEdge);
        if (edges != null && !edges.isEmpty())
        {
            Long[] edgeIds = edges.stream().map(ArchEdge::getId).toArray(Long[]::new);
            archEdgeService.deleteArchEdgeByIds(edgeIds);
        }
        return 1;
    }
}