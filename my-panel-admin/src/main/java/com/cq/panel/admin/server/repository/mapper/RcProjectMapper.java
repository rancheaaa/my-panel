package com.cq.panel.admin.server.repository.mapper;

import java.util.List;
import com.cq.panel.admin.server.repository.domain.RcProject;

/**
 * 应用管理Mapper接口
 * 
 * @author cq
 */
public interface RcProjectMapper 
{
    /**
     * 查询应用管理
     * 
     * @param id 应用管理主键
     * @return 应用管理
     */
    public RcProject selectRcProjectById(Long id);

    /**
     * 查询应用管理列表
     * 
     * @param rcProject 应用管理
     * @return 应用管理集合
     */
    public List<RcProject> selectRcProjectList(RcProject rcProject);

    /**
     * 新增应用管理
     * 
     * @param rcProject 应用管理
     * @return 结果
     */
    public int insertRcProject(RcProject rcProject);

    /**
     * 修改应用管理
     * 
     * @param rcProject 应用管理
     * @return 结果
     */
    public int updateRcProject(RcProject rcProject);

    /**
     * 删除应用管理
     * 
     * @param id 应用管理主键
     * @return 结果
     */
    public int deleteRcProjectById(Long id);

    /**
     * 批量删除应用管理
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteRcProjectByIds(Long[] ids);

    /**
     * 校验应用名称是否唯一
     * 
     * @param projectName 应用名称
     * @return 结果
     */
    public RcProject checkProjectNameUnique(String projectName);
}
