package com.cq.panel.admin.server.repository.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cq.panel.admin.server.repository.mapper.ArchDiagramShareMapper;
import com.cq.panel.admin.server.repository.domain.ArchDiagramShare;
import com.cq.panel.admin.server.repository.service.IArchDiagramShareService;

/**
 * 架构图分享 服务实现
 * 
 * @author cq
 */
@Service
public class ArchDiagramShareServiceImpl implements IArchDiagramShareService
{
    @Autowired
    private ArchDiagramShareMapper archDiagramShareMapper;

    @Override
    public List<ArchDiagramShare> selectArchDiagramShareList(ArchDiagramShare archDiagramShare)
    {
        return archDiagramShareMapper.selectArchDiagramShareList(archDiagramShare);
    }

    @Override
    public List<ArchDiagramShare> selectSharesByDiagramId(Long diagramId)
    {
        return archDiagramShareMapper.selectSharesByDiagramId(diagramId);
    }

    @Override
    public ArchDiagramShare selectArchDiagramShareByCode(String shareCode)
    {
        return archDiagramShareMapper.selectArchDiagramShareByCode(shareCode);
    }

    @Override
    public ArchDiagramShare selectArchDiagramShareById(Long id)
    {
        return archDiagramShareMapper.selectArchDiagramShareById(id);
    }

    @Override
    public int insertArchDiagramShare(ArchDiagramShare archDiagramShare)
    {
        return archDiagramShareMapper.insertArchDiagramShare(archDiagramShare);
    }

    @Override
    public int updateArchDiagramShare(ArchDiagramShare archDiagramShare)
    {
        return archDiagramShareMapper.updateArchDiagramShare(archDiagramShare);
    }

    @Override
    public int deleteArchDiagramShareById(Long id)
    {
        return archDiagramShareMapper.deleteArchDiagramShareById(id);
    }

    @Override
    public int deleteArchDiagramShareByIds(Long[] ids)
    {
        return archDiagramShareMapper.deleteArchDiagramShareByIds(ids);
    }

    @Override
    public int incrementViewCount(Long id)
    {
        return archDiagramShareMapper.incrementViewCount(id);
    }

    @Override
    public int revokeShare(Long id)
    {
        return archDiagramShareMapper.revokeShare(id);
    }

    @Override
    public boolean validateShare(String shareCode)
    {
        ArchDiagramShare share = archDiagramShareMapper.selectArchDiagramShareByCode(shareCode);
        if (share == null || !"1".equals(share.getStatus()))
        {
            return false;
        }
        if (share.getExpireTime() != null && share.getExpireTime().before(new Date()))
        {
            return false;
        }
        if (share.getMaxViewCount() != null && share.getCurrentViewCount() >= share.getMaxViewCount())
        {
            return false;
        }
        return true;
    }
}