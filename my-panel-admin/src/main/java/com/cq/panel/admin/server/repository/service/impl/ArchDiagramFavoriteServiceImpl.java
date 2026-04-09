package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cq.panel.admin.server.repository.mapper.ArchDiagramFavoriteMapper;
import com.cq.panel.admin.server.repository.domain.ArchDiagramFavorite;
import com.cq.panel.admin.server.repository.service.IArchDiagramFavoriteService;

/**
 * 架构图收藏 服务实现
 * 
 * @author cq
 */
@Service
public class ArchDiagramFavoriteServiceImpl implements IArchDiagramFavoriteService
{
    @Autowired
    private ArchDiagramFavoriteMapper archDiagramFavoriteMapper;

    @Override
    public List<ArchDiagramFavorite> selectArchDiagramFavoriteList(ArchDiagramFavorite archDiagramFavorite)
    {
        return archDiagramFavoriteMapper.selectArchDiagramFavoriteList(archDiagramFavorite);
    }

    @Override
    public List<ArchDiagramFavorite> selectFavoritesByUserId(Long userId)
    {
        return archDiagramFavoriteMapper.selectFavoritesByUserId(userId);
    }

    @Override
    public List<ArchDiagramFavorite> selectFavoritesByDiagramId(Long diagramId)
    {
        return archDiagramFavoriteMapper.selectFavoritesByDiagramId(diagramId);
    }

    @Override
    public ArchDiagramFavorite selectFavoriteByUserAndDiagram(Long userId, Long diagramId)
    {
        return archDiagramFavoriteMapper.selectFavoriteByUserAndDiagram(userId, diagramId);
    }

    @Override
    public int insertArchDiagramFavorite(ArchDiagramFavorite archDiagramFavorite)
    {
        return archDiagramFavoriteMapper.insertArchDiagramFavorite(archDiagramFavorite);
    }

    @Override
    public int deleteArchDiagramFavoriteById(Long id)
    {
        return archDiagramFavoriteMapper.deleteArchDiagramFavoriteById(id);
    }

    @Override
    public int deleteArchDiagramFavoriteByIds(Long[] ids)
    {
        return archDiagramFavoriteMapper.deleteArchDiagramFavoriteByIds(ids);
    }

    @Override
    public int cancelFavorite(Long userId, Long diagramId)
    {
        return archDiagramFavoriteMapper.cancelFavorite(userId, diagramId);
    }
}