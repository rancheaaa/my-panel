package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.ArchDiagramVersion;
import com.cq.panel.admin.server.repository.domain.ArchDiagramVersionData;

import java.util.List;
import java.util.Map;

/**
 * 架构图版本管理Service接口
 *
 * @author
 */
public interface IArchDiagramVersionService {
    /**
     * 创建版本快照
     *
     * @param diagramId 架构图ID
     * @param version 版本号
     * @param versionName 版本名称
     * @param changeSummary 变更摘要
     * @return 版本ID
     */
    public Long createVersionSnapshot(Long diagramId, String version, String versionName, String changeSummary);

    /**
     * 查询架构图的版本历史
     *
     * @param diagramId 架构图ID
     * @return 版本列表
     */
    public List<ArchDiagramVersion> getVersionHistory(Long diagramId);

    /**
     * 回滚到指定版本
     *
     * @param versionId 版本ID
     * @return 架构图ID
     */
    public Long restoreVersion(Long versionId);

    /**
     * 获取版本详细信息
     *
     * @param versionId 版本ID
     * @return 包含版本信息和数据的Map
     */
    public Map<String, Object> getVersionDetail(Long versionId);

    /**
     * 删除版本
     *
     * @param versionId 版本ID
     * @return 结果
     */
    public boolean deleteVersion(Long versionId);

    /**
     * 根据ID查询版本
     *
     * @param id 版本ID
     * @return 版本信息
     */
    public ArchDiagramVersion selectVersionById(Long id);

    /**
     * 查询版本数据
     *
     * @param versionId 版本ID
     * @return 版本数据列表
     */
    public List<ArchDiagramVersionData> selectVersionDataByVersionId(Long versionId);
}