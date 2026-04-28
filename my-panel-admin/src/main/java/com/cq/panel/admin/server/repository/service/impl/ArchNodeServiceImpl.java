package com.cq.panel.admin.server.repository.service.impl;

import java.util.Date;
import java.util.List;
import com.cq.panel.admin.server.common.utils.MyDateUtils;
import com.cq.panel.admin.server.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cq.panel.admin.server.repository.mapper.ArchNodeMapper;
import com.cq.panel.admin.server.repository.domain.ArchNode;
import com.cq.panel.admin.server.repository.service.IArchNodeService;

/**
 * 节点Service业务层处理
 * 
 * @author cq
 */
@Service
public class ArchNodeServiceImpl implements IArchNodeService 
{
    @Autowired
    private ArchNodeMapper archNodeMapper;

    /**
     * 查询节点
     * 
     * @param id 节点主键
     * @return 节点
     */
    @Override
    public ArchNode selectArchNodeById(Long id)
    {
        return archNodeMapper.selectArchNodeById(id);
    }

    /**
     * 查询节点列表
     * 
     * @param archNode 节点
     * @return 节点
     */
    @Override
    public List<ArchNode> selectArchNodeList(ArchNode archNode)
    {
        return archNodeMapper.selectArchNodeList(archNode);
    }

    /**
     * 通过架构图ID查询所有节点
     * 
     * @param diagramId 架构图ID
     * @return 节点列表
     */
    @Override
    public List<ArchNode> selectNodesByDiagramId(Long diagramId)
    {
        return archNodeMapper.selectNodesByDiagramId(diagramId);
    }

    /**
     * 新增节点
     * 
     * @param archNode 节点
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertArchNode(ArchNode archNode)
    {
        archNode.setCreateBy(SecurityUtils.getUsername());
        archNode.setCreateTime(MyDateUtils.getNowDate());
        archNode.setUpdateBy(SecurityUtils.getUsername());
        archNode.setUpdateTime(MyDateUtils.getNowDate());
        archNode.setStatus("0");
        archNode.setLocked("0");
        archNode.setVisible("1");
        archNode.setDelFlag("0");
        return archNodeMapper.insertArchNode(archNode);
    }

    /**
     * 批量新增节点
     * 
     * @param nodeList 节点列表
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchInsertArchNode(List<ArchNode> nodeList)
    {
        if (nodeList == null || nodeList.isEmpty())
        {
            return 0;
        }
        Date now = MyDateUtils.getNowDate();
        String username = SecurityUtils.getUsername();
        for (ArchNode archNode : nodeList)
        {
            archNode.setCreateBy(username);
            archNode.setCreateTime(now);
            archNode.setUpdateBy(username);
            archNode.setUpdateTime(now);
            archNode.setStatus("0");
            archNode.setLocked("0");
            archNode.setVisible("1");
            archNode.setDelFlag("0");
        }
        return archNodeMapper.batchInsertArchNode(nodeList);
    }

    /**
     * 修改节点
     * 
     * @param archNode 节点
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateArchNode(ArchNode archNode)
    {
        archNode.setUpdateBy(SecurityUtils.getUsername());
        archNode.setUpdateTime(MyDateUtils.getNowDate());
        return archNodeMapper.updateArchNode(archNode);
    }

    /**
     * 批量修改节点位置信息
     * 
     * @param nodeList 节点列表
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateArchNodePosition(List<ArchNode> nodeList)
    {
        if (nodeList == null || nodeList.isEmpty())
        {
            return 0;
        }
        Date now = MyDateUtils.getNowDate();
        String username = SecurityUtils.getUsername();
        int count = 0;
        for (ArchNode archNode : nodeList)
        {
            archNode.setUpdateBy(username);
            archNode.setUpdateTime(now);
            count += archNodeMapper.updateArchNode(archNode);
        }
        return count;
    }

    /**
     * 批量删除节点
     * 
     * @param ids 需要删除的节点主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteArchNodeByIds(Long[] ids)
    {
        return archNodeMapper.deleteArchNodeByIds(ids);
    }

    /**
     * 删除节点信息
     * 
     * @param id 节点主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteArchNodeById(Long id)
    {
        return archNodeMapper.deleteArchNodeById(id);
    }
}