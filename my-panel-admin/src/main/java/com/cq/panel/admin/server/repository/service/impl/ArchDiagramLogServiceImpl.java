package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cq.panel.admin.server.repository.mapper.ArchDiagramLogMapper;
import com.cq.panel.admin.server.repository.domain.ArchDiagramLog;
import com.cq.panel.admin.server.repository.service.IArchDiagramLogService;

/**
 * 架构图操作日志 服务实现
 * 
 * @author cq
 */
@Service
public class ArchDiagramLogServiceImpl implements IArchDiagramLogService
{
    @Autowired
    private ArchDiagramLogMapper archDiagramLogMapper;

    @Override
    public List<ArchDiagramLog> selectArchDiagramLogList(ArchDiagramLog archDiagramLog)
    {
        return archDiagramLogMapper.selectArchDiagramLogList(archDiagramLog);
    }

    @Override
    public List<ArchDiagramLog> selectLogsByDiagramId(Long diagramId)
    {
        return archDiagramLogMapper.selectLogsByDiagramId(diagramId);
    }

    @Override
    public ArchDiagramLog selectArchDiagramLogById(Long id)
    {
        return archDiagramLogMapper.selectArchDiagramLogById(id);
    }

    @Override
    public int insertArchDiagramLog(ArchDiagramLog archDiagramLog)
    {
        return archDiagramLogMapper.insertArchDiagramLog(archDiagramLog);
    }

    @Override
    @Transactional
    public int batchInsertArchDiagramLog(List<ArchDiagramLog> archDiagramLogs)
    {
        if (archDiagramLogs == null || archDiagramLogs.isEmpty())
        {
            return 0;
        }
        return archDiagramLogMapper.batchInsertArchDiagramLog(archDiagramLogs);
    }

    @Override
    public int deleteArchDiagramLogById(Long id)
    {
        return archDiagramLogMapper.deleteArchDiagramLogById(id);
    }

    @Override
    public int deleteArchDiagramLogByIds(Long[] ids)
    {
        return archDiagramLogMapper.deleteArchDiagramLogByIds(ids);
    }

    @Override
    public int clearLogsByDiagramId(Long diagramId)
    {
        return archDiagramLogMapper.clearLogsByDiagramId(diagramId);
    }
}