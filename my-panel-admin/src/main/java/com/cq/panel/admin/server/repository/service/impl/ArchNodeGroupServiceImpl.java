package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cq.panel.admin.server.repository.mapper.ArchNodeGroupMapper;
import com.cq.panel.admin.server.repository.domain.ArchNodeGroup;
import com.cq.panel.admin.server.repository.service.IArchNodeGroupService;

/**
 * 节点分组 服务实现
 * 
 * @author cq
 */
@Service
public class ArchNodeGroupServiceImpl implements IArchNodeGroupService
{
    @Autowired
    private ArchNodeGroupMapper archNodeGroupMapper;

    @Override
    public List<ArchNodeGroup> selectArchNodeGroupList(ArchNodeGroup archNodeGroup)
    {
        return archNodeGroupMapper.selectArchNodeGroupList(archNodeGroup);
    }

    @Override
    public List<ArchNodeGroup> selectGroupsByDiagramId(Long diagramId)
    {
        return archNodeGroupMapper.selectGroupsByDiagramId(diagramId);
    }

    @Override
    public ArchNodeGroup selectArchNodeGroupById(Long id)
    {
        return archNodeGroupMapper.selectArchNodeGroupById(id);
    }

    @Override
    public int insertArchNodeGroup(ArchNodeGroup archNodeGroup)
    {
        return archNodeGroupMapper.insertArchNodeGroup(archNodeGroup);
    }

    @Override
    public int updateArchNodeGroup(ArchNodeGroup archNodeGroup)
    {
        return archNodeGroupMapper.updateArchNodeGroup(archNodeGroup);
    }

    @Override
    public int deleteArchNodeGroupById(Long id)
    {
        return archNodeGroupMapper.deleteArchNodeGroupById(id);
    }

    @Override
    public int deleteArchNodeGroupByIds(Long[] ids)
    {
        return archNodeGroupMapper.deleteArchNodeGroupByIds(ids);
    }
}