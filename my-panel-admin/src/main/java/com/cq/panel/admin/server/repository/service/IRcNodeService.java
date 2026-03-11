package com.cq.panel.admin.server.repository.service;

import java.util.List;
import com.cq.panel.admin.server.repository.domain.RcNode;

/**
 * 注册中心节点Service接口
 * 
 * @author cq
 */
public interface IRcNodeService 
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
     * 批量删除注册中心节点
     * 
     * @param ids 需要删除的注册中心节点主键集合
     * @return 结果
     */
    public int deleteRcNodeByIds(Long[] ids);

    /**
     * 删除注册中心节点信息
     * 
     * @param id 注册中心节点主键
     * @return 结果
     */
    public int deleteRcNodeById(Long id);

    /**
     * 校验节点IP和端口是否唯一
     * 
     * @param rcNode 节点信息
     * @return 结果
     */
    public boolean checkNodeUnique(RcNode rcNode);
}
