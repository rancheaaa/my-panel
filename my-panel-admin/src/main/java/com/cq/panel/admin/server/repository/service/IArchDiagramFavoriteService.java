package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.ArchDiagramFavorite;
import java.util.List;

/**
 * 架构图收藏 服务层
 * 
 * @author cq
 */
public interface IArchDiagramFavoriteService
{
    /**
     * 查询架构图收藏集合
     * 
     * @param archDiagramFavorite 架构图收藏信息
     * @return 架构图收藏集合
     */
    List<ArchDiagramFavorite> selectArchDiagramFavoriteList(ArchDiagramFavorite archDiagramFavorite);

    /**
     * 通过用户ID查询收藏列表
     * 
     * @param userId 用户ID
     * @return 收藏列表
     */
    List<ArchDiagramFavorite> selectFavoritesByUserId(Long userId);

    /**
     * 通过架构图ID查询收藏用户列表
     * 
     * @param diagramId 架构图ID
     * @return 收藏用户列表
     */
    List<ArchDiagramFavorite> selectFavoritesByDiagramId(Long diagramId);

    /**
     * 检查是否已收藏
     * 
     * @param userId 用户ID
     * @param diagramId 架构图ID
     * @return 收藏信息
     */
    ArchDiagramFavorite selectFavoriteByUserAndDiagram(Long userId, Long diagramId);

    /**
     * 新增收藏信息
     * 
     * @param archDiagramFavorite 收藏信息
     * @return 结果
     */
    int insertArchDiagramFavorite(ArchDiagramFavorite archDiagramFavorite);

    /**
     * 删除收藏信息
     * 
     * @param id 收藏ID
     * @return 结果
     */
    int deleteArchDiagramFavoriteById(Long id);

    /**
     * 批量删除收藏信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteArchDiagramFavoriteByIds(Long[] ids);

    /**
     * 取消收藏
     * 
     * @param userId 用户ID
     * @param diagramId 架构图ID
     * @return 结果
     */
    int cancelFavorite(Long userId, Long diagramId);
}