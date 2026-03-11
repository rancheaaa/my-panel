package com.cq.panel.admin.server.repository.mapper;

import java.util.List;
import com.cq.panel.admin.server.repository.domain.RcNode;

/**
 * 注册中心节点Mapper接口
 * 
 * @author cq
 */
public interface RcNodeMapper 
{
    /**
     * 查询注册中心节点
     * 
     * @param id 注册中心节点主键
     * @return 注册中心节点
     */
    public RcNode selectRcNodeById(Long id);

    /**
     * 查询注册中心节点列表
     * 
     * @param rcNode 注册中心节点
     * @return 注册中心节点集合
     */
    public List<RcNode> selectRcNodeList(RcNode rcNode);

    /**
     * 新增注册中心节点
     * 
     * @param rcNode 注册中心节点
     * @return 结果
     */
    public int insertRcNode(RcNode rcNode);

    /**
     * 修改注册中心节点
     * 
     * @param rcNode 注册中心节点
     * @return 结果
     */
    public int updateRcNode(RcNode rcNode);

    /**
     * 删除注册中心节点
     * 
     * @param id 注册中心节点主键
     * @return 结果
     */
    public int deleteRcNodeById(Long id);

    /**
     * 批量删除注册中心节点
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteRcNodeByIds(Long[] ids);

    /**
     * 校验节点是否唯一
     * 
     * @param rcNode 节点信息
     * @return 结果
     */
    public RcNode checkNodeUnique(RcNode rcNode);

    /**
     * 批量下线超时节点
     * 
     * @param timeoutSeconds 超时时间（秒）
     * @return 结果
     */
    public int updateNodeOfflineByTimeout(Integer timeoutSeconds);
}
