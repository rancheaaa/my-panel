package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchDiagramFavorite;
import java.util.List;

/**
 * 架构图收藏 数据层
 * 
 * @author cq
 */
public interface ArchDiagramFavoriteMapper
{
    /**
     * 查询架构图收藏集合
     * 
     * @param archDiagramFavorite 架构图收藏信息
     * @return 架构图收藏集合
     */
    public List<ArchDiagramFavorite> selectArchDiagramFavoriteList(ArchDiagramFavorite archDiagramFavorite);

    /**
     * 通过用户ID查询收藏列表
     * 
     * @param userId 用户ID
     * @return 收藏列表
     */
    public List<ArchDiagramFavorite> selectFavoritesByUserId(Long userId);

    /**
     * 通过架构图ID查询收藏用户列表
     * 
     * @param diagramId 架构图ID
     * @return 收藏用户列表
     */
    public List<ArchDiagramFavorite> selectFavoritesByDiagramId(Long diagramId);

    /**
     * 检查是否已收藏
     * 
     * @param userId 用户ID
     * @param diagramId 架构图ID
     * @return 收藏信息
     */
    public ArchDiagramFavorite selectFavoriteByUserAndDiagram(Long userId, Long diagramId);

    /**
     * 通过收藏ID删除收藏信息
     * 
     * @param id 收藏ID
     * @return 结果
     */
    public int deleteArchDiagramFavoriteById(Long id);

    /**
     * 批量删除收藏信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteArchDiagramFavoriteByIds(Long[] ids);

    /**
     * 取消收藏
     * 
     * @param userId 用户ID
     * @param diagramId 架构图ID
     * @return 结果
     */
    public int cancelFavorite(Long userId, Long diagramId);

    /**
     * 新增收藏信息
     * 
     * @param archDiagramFavorite 收藏信息
     * @return 结果
     */
    public int insertArchDiagramFavorite(ArchDiagramFavorite archDiagramFavorite);
}