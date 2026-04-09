package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.ArchDiagramShare;
import java.util.List;

/**
 * 架构图分享 服务层
 * 
 * @author cq
 */
public interface IArchDiagramShareService
{
    /**
     * 查询架构图分享集合
     * 
     * @param archDiagramShare 架构图分享信息
     * @return 架构图分享集合
     */
    List<ArchDiagramShare> selectArchDiagramShareList(ArchDiagramShare archDiagramShare);

    /**
     * 通过架构图ID查询分享列表
     * 
     * @param diagramId 架构图ID
     * @return 分享列表
     */
    List<ArchDiagramShare> selectSharesByDiagramId(Long diagramId);

    /**
     * 通过分享码查询分享信息
     * 
     * @param shareCode 分享码
     * @return 分享对象信息
     */
    ArchDiagramShare selectArchDiagramShareByCode(String shareCode);

    /**
     * 通过分享ID查询分享信息
     * 
     * @param id 分享ID
     * @return 分享对象信息
     */
    ArchDiagramShare selectArchDiagramShareById(Long id);

    /**
     * 新增分享信息
     * 
     * @param archDiagramShare 分享信息
     * @return 结果
     */
    int insertArchDiagramShare(ArchDiagramShare archDiagramShare);

    /**
     * 修改分享信息
     * 
     * @param archDiagramShare 分享信息
     * @return 结果
     */
    int updateArchDiagramShare(ArchDiagramShare archDiagramShare);

    /**
     * 删除分享信息
     * 
     * @param id 分享ID
     * @return 结果
     */
    int deleteArchDiagramShareById(Long id);

    /**
     * 批量删除分享信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteArchDiagramShareByIds(Long[] ids);

    /**
     * 增加查看次数
     * 
     * @param id 分享ID
     * @return 结果
     */
    int incrementViewCount(Long id);

    /**
     * 撤销分享
     * 
     * @param id 分享ID
     * @return 结果
     */
    int revokeShare(Long id);

    /**
     * 验证分享是否有效
     * 
     * @param shareCode 分享码
     * @return 是否有效
     */
    boolean validateShare(String shareCode);
}