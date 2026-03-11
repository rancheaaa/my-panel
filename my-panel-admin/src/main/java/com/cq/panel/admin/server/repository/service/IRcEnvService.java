package com.cq.panel.admin.server.repository.service;

import java.util.List;
import com.cq.panel.admin.server.repository.domain.RcEnv;

/**
 * 环境管理Service接口
 * 
 * @author cq
 */
public interface IRcEnvService 
{
    /**
     * 查询环境管理
     * 
     * @param id 环境管理主键
     * @return 环境管理
     */
    public RcEnv selectRcEnvById(Long id);

    /**
     * 查询环境管理列表
     * 
     * @param rcEnv 环境管理
     * @return 环境管理集合
     */
    public List<RcEnv> selectRcEnvList(RcEnv rcEnv);

    /**
     * 新增环境管理
     * 
     * @param rcEnv 环境管理
     * @return 结果
     */
    public int insertRcEnv(RcEnv rcEnv);

    /**
     * 修改环境管理
     * 
     * @param rcEnv 环境管理
     * @return 结果
     */
    public int updateRcEnv(RcEnv rcEnv);

    /**
     * 批量删除环境管理
     * 
     * @param ids 需要删除的环境管理主键集合
     * @return 结果
     */
    public int deleteRcEnvByIds(Long[] ids);

    /**
     * 删除环境管理信息
     * 
     * @param id 环境管理主键
     * @return 结果
     */
    public int deleteRcEnvById(Long id);

    /**
     * 校验环境名称是否唯一
     * 
     * @param rcEnv 环境信息
     * @return 结果
     */
    public boolean checkEnvNameUnique(RcEnv rcEnv);
}
