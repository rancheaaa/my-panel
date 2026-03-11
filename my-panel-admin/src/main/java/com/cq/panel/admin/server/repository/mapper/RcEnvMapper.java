package com.cq.panel.admin.server.repository.mapper;

import java.util.List;
import com.cq.panel.admin.server.repository.domain.RcEnv;

/**
 * 环境管理Mapper接口
 * 
 * @author cq
 */
public interface RcEnvMapper 
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
     * 删除环境管理
     * 
     * @param id 环境管理主键
     * @return 结果
     */
    public int deleteRcEnvById(Long id);

    /**
     * 批量删除环境管理
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteRcEnvByIds(Long[] ids);

    /**
     * 校验环境名称是否唯一
     * 
     * @param envName 环境名称
     * @return 结果
     */
    public RcEnv checkEnvNameUnique(String envName);
}
