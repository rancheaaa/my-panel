package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import com.cq.panel.admin.server.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cq.panel.admin.server.repository.mapper.RcProjectMapper;
import com.cq.panel.admin.server.repository.domain.RcProject;
import com.cq.panel.admin.server.repository.service.IRcProjectService;
import com.cq.panel.admin.server.common.constant.UserConstants;
import com.cq.panel.admin.server.common.utils.StringUtils;

/**
 * 应用管理Service业务层处理
 * 
 * @author cq
 */
@Service
public class RcProjectServiceImpl implements IRcProjectService 
{
    @Autowired
    private RcProjectMapper rcProjectMapper;

    /**
     * 查询应用管理
     * 
     * @param id 应用管理主键
     * @return 应用管理
     */
    @Override
    public RcProject selectRcProjectById(Long id)
    {
        return rcProjectMapper.selectRcProjectById(id);
    }

    /**
     * 查询应用管理列表
     * 
     * @param rcProject 应用管理
     * @return 应用管理
     */
    @Override
    public List<RcProject> selectRcProjectList(RcProject rcProject)
    {
        return rcProjectMapper.selectRcProjectList(rcProject);
    }

    /**
     * 新增应用管理
     * 
     * @param rcProject 应用管理
     * @return 结果
     */
    @Override
    public int insertRcProject(RcProject rcProject)
    {
        rcProject.setCreateTime(DateUtils.getNowDate());
        return rcProjectMapper.insertRcProject(rcProject);
    }

    /**
     * 修改应用管理
     * 
     * @param rcProject 应用管理
     * @return 结果
     */
    @Override
    public int updateRcProject(RcProject rcProject)
    {
        rcProject.setUpdateTime(DateUtils.getNowDate());
        return rcProjectMapper.updateRcProject(rcProject);
    }

    /**
     * 批量删除应用管理
     * 
     * @param ids 需要删除的应用管理主键
     * @return 结果
     */
    @Override
    public int deleteRcProjectByIds(Long[] ids)
    {
        return rcProjectMapper.deleteRcProjectByIds(ids);
    }

    /**
     * 删除应用管理信息
     * 
     * @param id 应用管理主键
     * @return 结果
     */
    @Override
    public int deleteRcProjectById(Long id)
    {
        return rcProjectMapper.deleteRcProjectById(id);
    }

    /**
     * 校验项目名称是否唯一
     * 
     * @param rcProject 项目信息
     * @return 结果
     */
    @Override
    public boolean checkProjectNameUnique(RcProject rcProject)
    {
        Long id = StringUtils.isNull(rcProject.getId()) ? -1L : rcProject.getId();
        RcProject info = rcProjectMapper.checkProjectNameUnique(rcProject.getProjectName());
        if (StringUtils.isNotNull(info) && info.getId().longValue() != id.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }
}
