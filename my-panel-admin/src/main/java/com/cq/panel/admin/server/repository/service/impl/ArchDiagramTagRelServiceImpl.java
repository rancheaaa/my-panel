package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cq.panel.admin.server.repository.mapper.ArchDiagramTagRelMapper;
import com.cq.panel.admin.server.repository.domain.ArchDiagramTagRel;
import com.cq.panel.admin.server.repository.service.IArchDiagramTagRelService;

/**
 * 架构图标签关联 服务实现
 * 
 * @author cq
 */
@Service
public class ArchDiagramTagRelServiceImpl implements IArchDiagramTagRelService
{
    @Autowired
    private ArchDiagramTagRelMapper archDiagramTagRelMapper;

    @Override
    public List<ArchDiagramTagRel> selectArchDiagramTagRelList(ArchDiagramTagRel archDiagramTagRel)
    {
        return archDiagramTagRelMapper.selectArchDiagramTagRelList(archDiagramTagRel);
    }

    @Override
    public List<Long> selectTagIdsByDiagramId(Long diagramId)
    {
        return archDiagramTagRelMapper.selectTagIdsByDiagramId(diagramId);
    }

    @Override
    public List<Long> selectDiagramIdsByTagId(Long tagId)
    {
        return archDiagramTagRelMapper.selectDiagramIdsByTagId(tagId);
    }

    @Override
    public int insertArchDiagramTagRel(ArchDiagramTagRel archDiagramTagRel)
    {
        return archDiagramTagRelMapper.insertArchDiagramTagRel(archDiagramTagRel);
    }

    @Override
    @Transactional
    public int batchInsertArchDiagramTagRel(List<ArchDiagramTagRel> archDiagramTagRels)
    {
        if (archDiagramTagRels == null || archDiagramTagRels.isEmpty())
        {
            return 0;
        }
        return archDiagramTagRelMapper.batchInsertArchDiagramTagRel(archDiagramTagRels);
    }

    @Override
    public int deleteArchDiagramTagRelById(Long id)
    {
        return archDiagramTagRelMapper.deleteArchDiagramTagRelById(id);
    }

    @Override
    public int deleteArchDiagramTagRelByIds(Long[] ids)
    {
        return archDiagramTagRelMapper.deleteArchDiagramTagRelByIds(ids);
    }

    @Override
    public int deleteArchDiagramTagRelByDiagramId(Long diagramId)
    {
        return archDiagramTagRelMapper.deleteArchDiagramTagRelByDiagramId(diagramId);
    }

    @Override
    public int deleteArchDiagramTagRelByTagId(Long tagId)
    {
        return archDiagramTagRelMapper.deleteArchDiagramTagRelByTagId(tagId);
    }
}