package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.web.domain.vo.batch.DirectoryCheckVO;

public interface IDirectoryCheckService {

    DirectoryCheckVO.DirectoryCheckResult checkDirectories(Long taskId);

    DirectoryCheckVO checkSingleDirectory(String agentId, String dirPath);
}
