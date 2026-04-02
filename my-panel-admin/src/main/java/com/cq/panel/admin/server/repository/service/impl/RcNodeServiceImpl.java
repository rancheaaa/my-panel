package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import com.cq.panel.admin.server.common.utils.DateUtils;
import com.cq.panel.admin.server.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cq.panel.admin.server.repository.mapper.RcNodeMapper;
import com.cq.panel.admin.server.repository.domain.RcNode;
import com.cq.panel.admin.server.repository.service.IRcNodeService;

/**
 * 注册中心节点Service业务层处理
 * 
 * @author cq
 */
@Service
public class RcNodeServiceImpl implements IRcNodeService 
{
    @Autowired
    private RcNodeMapper rcNodeMapper;

    /**
     * 查询注册中心节点
     * 
     * @param id 注册中心节点主键
     * @return 注册中心节点
     */
    @Override
    public RcNode selectRcNodeById(Long id)
    {
        return rcNodeMapper.selectRcNodeById(id);
    }

    /**
     * 查询注册中心节点列表
     * 
     * @param rcNode 注册中心节点
     * @return 注册中心节点
     */
    @Override
    public List<RcNode> selectRcNodeList(RcNode rcNode)
    {
        return rcNodeMapper.selectRcNodeList(rcNode);
    }

    /**
     * 新增注册中心节点
     * 
     * @param rcNode 注册中心节点
     * @return 结果
     */
    @Override
    public int insertRcNode(RcNode rcNode)
    {
        rcNode.setCreateTime(DateUtils.getNowDate());
        rcNode.setStatus("0"); // 默认在线
        // 设置zone默认值
        if (rcNode.getZone() == null || rcNode.getZone().isEmpty()) {
            rcNode.setZone("default");
        }
        rcNode.setLastRefreshTime(DateUtils.getNowDate());
        return rcNodeMapper.insertRcNode(rcNode);
    }

    /**
     * 修改注册中心节点
     * 
     * @param rcNode 注册中心节点
     * @return 结果
     */
    @Override
    public int updateRcNode(RcNode rcNode)
    {
        return rcNodeMapper.updateRcNode(rcNode);
    }

    /**
     * 批量删除注册中心节点
     * 
     * @param ids 需要删除的注册中心节点主键
     * @return 结果
     */
    @Override
    public int deleteRcNodeByIds(Long[] ids)
    {
        return rcNodeMapper.deleteRcNodeByIds(ids);
    }

    /**
     * 删除注册中心节点信息
     * 
     * @param id 注册中心节点主键
     * @return 结果
     */
    @Override
    public int deleteRcNodeById(Long id)
    {
        return rcNodeMapper.deleteRcNodeById(id);
    }

    /**
     * 校验节点IP和端口是否唯一
     * 
     * @param rcNode 节点信息
     * @return 结果
     */
    @Override
    public boolean checkNodeUnique(RcNode rcNode)
    {
        Long id = StringUtils.isNull(rcNode.getId()) ? -1L : rcNode.getId();
        RcNode info = rcNodeMapper.checkNodeUnique(rcNode);
        if (StringUtils.isNotNull(info) && info.getId().longValue() != id.longValue())
        {
            return false;
        }
        return true;
    }
}