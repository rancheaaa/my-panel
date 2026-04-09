package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchDiagramShare;
import java.util.List;

/**
 * 架构图分享 数据层
 * 
 * @author cq
 */
public interface ArchDiagramShareMapper
{
    /**
     * 查询架构图分享集合
     * 
     * @param archDiagramShare 架构图分享信息
     * @return 架构图分享集合
     */
    public List<ArchDiagramShare> selectArchDiagramShareList(ArchDiagramShare archDiagramShare);

    /**
     * 通过架构图ID查询分享列表
     * 
     * @param diagramId 架构图ID
     * @return 分享列表
     */
    public List<ArchDiagramShare> selectSharesByDiagramId(Long diagramId);

    /**
     * 通过分享码查询分享信息
     * 
     * @param shareCode 分享码
     * @return 分享对象信息
     */
    public ArchDiagramShare selectArchDiagramShareByCode(String shareCode);

    /**
     * 通过分享ID查询分享信息
     * 
     * @param id 分享ID
     * @return 分享对象信息
     */
    public ArchDiagramShare selectArchDiagramShareById(Long id);

    /**
     * 通过分享ID删除分享信息
     * 
     * @param id 分享ID
     * @return 结果
     */
    public int deleteArchDiagramShareById(Long id);

    /**
     * 批量删除分享信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteArchDiagramShareByIds(Long[] ids);

    /**
     * 修改分享信息
     * 
     * @param archDiagramShare 分享信息
     * @return 结果
     */
    public int updateArchDiagramShare(ArchDiagramShare archDiagramShare);

    /**
     * 新增分享信息
     * 
     * @param archDiagramShare 分享信息
     * @return 结果
     */
    public int insertArchDiagramShare(ArchDiagramShare archDiagramShare);

    /**
     * 增加查看次数
     * 
     * @param id 分享ID
     * @return 结果
     */
    public int incrementViewCount(Long id);

    /**
     * 撤销分享
     * 
     * @param id 分享ID
     * @return 结果
     */
    public int revokeShare(Long id);
}