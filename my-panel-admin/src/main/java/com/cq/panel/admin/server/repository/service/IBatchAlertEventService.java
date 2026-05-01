package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.BatchAlertEvent;
import java.util.List;

public interface IBatchAlertEventService
{
    int insert(BatchAlertEvent entity);

    List<BatchAlertEvent> selectUnresolved();

    List<BatchAlertEvent> selectRecent(int limit);

    int resolve(Long id, String resolvedBy, String resolutionNote);
}
