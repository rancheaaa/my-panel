package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cq.panel.admin.server.repository.mapper.ArchNodeGroupRelMapper;
import com.cq.panel.admin.server.repository.domain.ArchNodeGroupRel;
import com.cq.panel.admin.server.repository.service.IArchNodeGroupRelService;

/**
 * 节点分组关联 服务实现
 * 
 * @author cq
 */
@Service
public class ArchNodeGroupRelServiceImpl implements IArchNodeGroupRelService
{
    @Autowired
    private ArchNodeGroupRelMapper archNodeGroupRelMapper;

    @Override
    public List<ArchNodeGroupRel> selectArchNodeGroupRelList(ArchNodeGroupRel archNodeGroupRel)
    {
        return archNodeGroupRelMapper.selectArchNodeGroupRelList(archNodeGroupRel);
    }

    @Override
    public List<Long> selectNodeIdsByGroupId(Long groupId)
    {
        return archNodeGroupRelMapper.selectNodeIdsByGroupId(groupId);
    }

    @Override
    public List<Long> selectGroupIdsByNodeId(Long nodeId)
    {
        return archNodeGroupRelMapper.selectGroupIdsByNodeId(nodeId);
    }

    @Override
    public int insertArchNodeGroupRel(ArchNodeGroupRel archNodeGroupRel)
    {
        return archNodeGroupRelMapper.insertArchNodeGroupRel(archNodeGroupRel);
    }

    @Override
    @Transactional
    public int batchInsertArchNodeGroupRel(List<ArchNodeGroupRel> archNodeGroupRels)
    {
        if (archNodeGroupRels == null || archNodeGroupRels.isEmpty())
        {
            return 0;
        }
        return archNodeGroupRelMapper.batchInsertArchNodeGroupRel(archNodeGroupRels);
    }

    @Override
    public int deleteArchNodeGroupRelById(Long id)
    {
        return archNodeGroupRelMapper.deleteArchNodeGroupRelById(id);
    }

    @Override
    public int deleteArchNodeGroupRelByIds(Long[] ids)
    {
        return archNodeGroupRelMapper.deleteArchNodeGroupRelByIds(ids);
    }

    @Override
    public int deleteArchNodeGroupRelByGroupId(Long groupId)
    {
        return archNodeGroupRelMapper.deleteArchNodeGroupRelByGroupId(groupId);
    }

    @Override
    public int deleteArchNodeGroupRelByNodeId(Long nodeId)
    {
        return archNodeGroupRelMapper.deleteArchNodeGroupRelByNodeId(nodeId);
    }
}