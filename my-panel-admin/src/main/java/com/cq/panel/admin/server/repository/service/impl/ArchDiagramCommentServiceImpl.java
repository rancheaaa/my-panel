package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cq.panel.admin.server.repository.mapper.ArchDiagramCommentMapper;
import com.cq.panel.admin.server.repository.domain.ArchDiagramComment;
import com.cq.panel.admin.server.repository.service.IArchDiagramCommentService;

/**
 * 架构图评论 服务实现
 * 
 * @author cq
 */
@Service
public class ArchDiagramCommentServiceImpl implements IArchDiagramCommentService
{
    @Autowired
    private ArchDiagramCommentMapper archDiagramCommentMapper;

    @Override
    public List<ArchDiagramComment> selectArchDiagramCommentList(ArchDiagramComment archDiagramComment)
    {
        return archDiagramCommentMapper.selectArchDiagramCommentList(archDiagramComment);
    }

    @Override
    public List<ArchDiagramComment> selectCommentsByDiagramId(Long diagramId)
    {
        return archDiagramCommentMapper.selectCommentsByDiagramId(diagramId);
    }

    @Override
    public List<ArchDiagramComment> selectCommentsByNodeId(Long nodeId)
    {
        return archDiagramCommentMapper.selectCommentsByNodeId(nodeId);
    }

    @Override
    public List<ArchDiagramComment> selectCommentsByEdgeId(Long edgeId)
    {
        return archDiagramCommentMapper.selectCommentsByEdgeId(edgeId);
    }

    @Override
    public List<ArchDiagramComment> selectRepliesByParentId(Long parentCommentId)
    {
        return archDiagramCommentMapper.selectRepliesByParentId(parentCommentId);
    }

    @Override
    public ArchDiagramComment selectArchDiagramCommentById(Long id)
    {
        return archDiagramCommentMapper.selectArchDiagramCommentById(id);
    }

    @Override
    public int insertArchDiagramComment(ArchDiagramComment archDiagramComment)
    {
        return archDiagramCommentMapper.insertArchDiagramComment(archDiagramComment);
    }

    @Override
    public int updateArchDiagramComment(ArchDiagramComment archDiagramComment)
    {
        return archDiagramCommentMapper.updateArchDiagramComment(archDiagramComment);
    }

    @Override
    public int deleteArchDiagramCommentById(Long id)
    {
        return archDiagramCommentMapper.deleteArchDiagramCommentById(id);
    }

    @Override
    public int deleteArchDiagramCommentByIds(Long[] ids)
    {
        return archDiagramCommentMapper.deleteArchDiagramCommentByIds(ids);
    }

    @Override
    public int incrementLikeCount(Long id)
    {
        return archDiagramCommentMapper.incrementLikeCount(id);
    }

    @Override
    public int incrementReplyCount(Long id)
    {
        return archDiagramCommentMapper.incrementReplyCount(id);
    }

    @Override
    public int markAsResolved(Long id)
    {
        return archDiagramCommentMapper.markAsResolved(id);
    }
}