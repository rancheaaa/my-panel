package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cq.panel.admin.server.repository.mapper.ArchDiagramHistoryMapper;
import com.cq.panel.admin.server.repository.domain.ArchDiagramHistory;
import com.cq.panel.admin.server.repository.service.IArchDiagramHistoryService;

/**
 * 架构图版本历史 服务实现
 * 
 * @author cq
 */
@Service
public class ArchDiagramHistoryServiceImpl implements IArchDiagramHistoryService
{
    @Autowired
    private ArchDiagramHistoryMapper archDiagramHistoryMapper;

    @Override
    public List<ArchDiagramHistory> selectArchDiagramHistoryList(ArchDiagramHistory archDiagramHistory)
    {
        return archDiagramHistoryMapper.selectArchDiagramHistoryList(archDiagramHistory);
    }

    @Override
    public List<ArchDiagramHistory> selectHistoryByDiagramId(Long diagramId)
    {
        return archDiagramHistoryMapper.selectHistoryByDiagramId(diagramId);
    }

    @Override
    public ArchDiagramHistory selectArchDiagramHistoryById(Long id)
    {
        return archDiagramHistoryMapper.selectArchDiagramHistoryById(id);
    }

    @Override
    public ArchDiagramHistory selectCurrentVersion(Long diagramId)
    {
        return archDiagramHistoryMapper.selectCurrentVersion(diagramId);
    }

    @Override
    public int insertArchDiagramHistory(ArchDiagramHistory archDiagramHistory)
    {
        return archDiagramHistoryMapper.insertArchDiagramHistory(archDiagramHistory);
    }

    @Override
    public int updateArchDiagramHistory(ArchDiagramHistory archDiagramHistory)
    {
        return archDiagramHistoryMapper.updateArchDiagramHistory(archDiagramHistory);
    }

    @Override
    public int deleteArchDiagramHistoryById(Long id)
    {
        return archDiagramHistoryMapper.deleteArchDiagramHistoryById(id);
    }

    @Override
    public int deleteArchDiagramHistoryByIds(Long[] ids)
    {
        return archDiagramHistoryMapper.deleteArchDiagramHistoryByIds(ids);
    }

    @Override
    @Transactional
    public int createSnapshot(Long diagramId, String version, String versionName, String changeSummary)
    {
        archDiagramHistoryMapper.updateCurrentVersion(diagramId);
        ArchDiagramHistory history = new ArchDiagramHistory();
        history.setDiagramId(diagramId);
        history.setVersion(version);
        history.setVersionName(versionName);
        history.setChangeSummary(changeSummary);
        history.setIsCurrent("1");
        return archDiagramHistoryMapper.insertArchDiagramHistory(history);
    }

    @Override
    @Transactional
    public int restoreVersion(Long historyId)
    {
        ArchDiagramHistory history = archDiagramHistoryMapper.selectArchDiagramHistoryById(historyId);
        if (history == null)
        {
            return 0;
        }
        archDiagramHistoryMapper.updateCurrentVersion(history.getDiagramId());
        return archDiagramHistoryMapper.updateHistoryToCurrent(historyId);
    }
}