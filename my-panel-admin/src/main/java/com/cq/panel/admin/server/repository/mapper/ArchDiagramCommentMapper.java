package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchDiagramComment;
import java.util.List;

/**
 * 架构图评论 数据层
 * 
 * @author cq
 */
public interface ArchDiagramCommentMapper
{
    /**
     * 查询架构图评论集合
     * 
     * @param archDiagramComment 架构图评论信息
     * @return 架构图评论集合
     */
    public List<ArchDiagramComment> selectArchDiagramCommentList(ArchDiagramComment archDiagramComment);

    /**
     * 通过架构图ID查询评论列表
     * 
     * @param diagramId 架构图ID
     * @return 评论列表
     */
    public List<ArchDiagramComment> selectCommentsByDiagramId(Long diagramId);

    /**
     * 通过节点ID查询评论列表
     * 
     * @param nodeId 节点ID
     * @return 评论列表
     */
    public List<ArchDiagramComment> selectCommentsByNodeId(Long nodeId);

    /**
     * 通过边缘ID查询评论列表
     * 
     * @param edgeId 边缘ID
     * @return 评论列表
     */
    public List<ArchDiagramComment> selectCommentsByEdgeId(Long edgeId);

    /**
     * 通过父评论ID查询回复列表
     * 
     * @param parentCommentId 父评论ID
     * @return 回复列表
     */
    public List<ArchDiagramComment> selectRepliesByParentId(Long parentCommentId);

    /**
     * 通过评论ID查询评论信息
     * 
     * @param id 评论ID
     * @return 评论对象信息
     */
    public ArchDiagramComment selectArchDiagramCommentById(Long id);

    /**
     * 通过评论ID删除评论信息
     * 
     * @param id 评论ID
     * @return 结果
     */
    public int deleteArchDiagramCommentById(Long id);

    /**
     * 批量删除评论信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteArchDiagramCommentByIds(Long[] ids);

    /**
     * 修改评论信息
     * 
     * @param archDiagramComment 评论信息
     * @return 结果
     */
    public int updateArchDiagramComment(ArchDiagramComment archDiagramComment);

    /**
     * 新增评论信息
     * 
     * @param archDiagramComment 评论信息
     * @return 结果
     */
    public int insertArchDiagramComment(ArchDiagramComment archDiagramComment);

    /**
     * 增加点赞数
     * 
     * @param id 评论ID
     * @return 结果
     */
    public int incrementLikeCount(Long id);

    /**
     * 增加回复数
     * 
     * @param id 评论ID
     * @return 结果
     */
    public int incrementReplyCount(Long id);

    /**
     * 标记评论为已解决
     * 
     * @param id 评论ID
     * @return 结果
     */
    public int markAsResolved(Long id);
}