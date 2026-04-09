package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cq.panel.admin.server.repository.mapper.ArchDiagramTagMapper;
import com.cq.panel.admin.server.repository.domain.ArchDiagramTag;
import com.cq.panel.admin.server.repository.service.IArchDiagramTagService;

/**
 * 架构图标签 服务实现
 * 
 * @author cq
 */
@Service
public class ArchDiagramTagServiceImpl implements IArchDiagramTagService
{
    @Autowired
    private ArchDiagramTagMapper archDiagramTagMapper;

    @Override
    public List<ArchDiagramTag> selectArchDiagramTagList(ArchDiagramTag archDiagramTag)
    {
        return archDiagramTagMapper.selectArchDiagramTagList(archDiagramTag);
    }

    @Override
    public List<ArchDiagramTag> selectArchDiagramTagAll()
    {
        return archDiagramTagMapper.selectArchDiagramTagAll();
    }

    @Override
    public ArchDiagramTag selectArchDiagramTagById(Long id)
    {
        return archDiagramTagMapper.selectArchDiagramTagById(id);
    }

    @Override
    public ArchDiagramTag selectArchDiagramTagByName(String tagName)
    {
        return archDiagramTagMapper.selectArchDiagramTagByName(tagName);
    }

    @Override
    public int insertArchDiagramTag(ArchDiagramTag archDiagramTag)
    {
        return archDiagramTagMapper.insertArchDiagramTag(archDiagramTag);
    }

    @Override
    public int updateArchDiagramTag(ArchDiagramTag archDiagramTag)
    {
        return archDiagramTagMapper.updateArchDiagramTag(archDiagramTag);
    }

    @Override
    public int deleteArchDiagramTagById(Long id)
    {
        return archDiagramTagMapper.deleteArchDiagramTagById(id);
    }

    @Override
    public int deleteArchDiagramTagByIds(Long[] ids)
    {
        return archDiagramTagMapper.deleteArchDiagramTagByIds(ids);
    }

    @Override
    public int incrementUseCount(Long id)
    {
        return archDiagramTagMapper.incrementUseCount(id);
    }

    @Override
    public boolean checkTagNameUnique(ArchDiagramTag archDiagramTag)
    {
        ArchDiagramTag unique = archDiagramTagMapper.checkTagNameUnique(archDiagramTag);
        return unique == null || unique.getId().equals(archDiagramTag.getId());
    }
}