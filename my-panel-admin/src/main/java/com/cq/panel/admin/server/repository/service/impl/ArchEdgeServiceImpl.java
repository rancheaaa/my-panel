package com.cq.panel.admin.server.repository.service.impl;

import java.util.Date;
import java.util.List;
import com.cq.panel.admin.server.common.utils.MyDateUtils;
import com.cq.panel.admin.server.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cq.panel.admin.server.repository.mapper.ArchEdgeMapper;
import com.cq.panel.admin.server.repository.domain.ArchEdge;
import com.cq.panel.admin.server.repository.service.IArchEdgeService;

/**
 * 边缘Service业务层处理
 * 
 * @author cq
 */
@Service
public class ArchEdgeServiceImpl implements IArchEdgeService 
{
    @Autowired
    private ArchEdgeMapper archEdgeMapper;

    /**
     * 查询边缘
     * 
     * @param id 边缘主键
     * @return 边缘
     */
    @Override
    public ArchEdge selectArchEdgeById(Long id)
    {
        return archEdgeMapper.selectArchEdgeById(id);
    }

    /**
     * 查询边缘列表
     * 
     * @param archEdge 边缘
     * @return 边缘
     */
    @Override
    public List<ArchEdge> selectArchEdgeList(ArchEdge archEdge)
    {
        return archEdgeMapper.selectArchEdgeList(archEdge);
    }

    /**
     * 通过架构图ID查询所有边缘
     * 
     * @param diagramId 架构图ID
     * @return 边缘列表
     */
    @Override
    public List<ArchEdge> selectEdgesByDiagramId(Long diagramId)
    {
        return archEdgeMapper.selectEdgesByDiagramId(diagramId);
    }

    /**
     * 新增边缘
     * 
     * @param archEdge 边缘
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertArchEdge(ArchEdge archEdge)
    {
        archEdge.setCreateBy(SecurityUtils.getUsername());
        archEdge.setCreateTime(MyDateUtils.getNowDate());
        archEdge.setUpdateBy(SecurityUtils.getUsername());
        archEdge.setUpdateTime(MyDateUtils.getNowDate());
        archEdge.setStatus("0");
        archEdge.setVisible("1");
        archEdge.setDelFlag("0");
        return archEdgeMapper.insertArchEdge(archEdge);
    }

    /**
     * 批量新增边缘
     * 
     * @param edgeList 边缘列表
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchInsertArchEdge(List<ArchEdge> edgeList)
    {
        if (edgeList == null || edgeList.isEmpty())
        {
            return 0;
        }
        Date now = MyDateUtils.getNowDate();
        String username = SecurityUtils.getUsername();
        for (ArchEdge archEdge : edgeList)
        {
            archEdge.setCreateBy(username);
            archEdge.setCreateTime(now);
            archEdge.setUpdateBy(username);
            archEdge.setUpdateTime(now);
            archEdge.setStatus("0");
            archEdge.setVisible("1");
            archEdge.setDelFlag("0");
        }
        return archEdgeMapper.batchInsertArchEdge(edgeList);
    }

    /**
     * 修改边缘
     * 
     * @param archEdge 边缘
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateArchEdge(ArchEdge archEdge)
    {
        archEdge.setUpdateBy(SecurityUtils.getUsername());
        archEdge.setUpdateTime(MyDateUtils.getNowDate());
        return archEdgeMapper.updateArchEdge(archEdge);
    }

    /**
     * 批量删除边缘
     * 
     * @param ids 需要删除的边缘主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteArchEdgeByIds(Long[] ids)
    {
        return archEdgeMapper.deleteArchEdgeByIds(ids);
    }

    /**
     * 删除边缘信息
     * 
     * @param id 边缘主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteArchEdgeById(Long id)
    {
        return archEdgeMapper.deleteArchEdgeById(id);
    }
}