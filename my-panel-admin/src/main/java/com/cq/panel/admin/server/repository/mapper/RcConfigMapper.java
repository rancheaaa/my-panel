package com.cq.panel.admin.server.repository.mapper;

import java.util.List;
import com.cq.panel.admin.server.repository.domain.RcConfig;
import org.apache.ibatis.annotations.Param;

/**
 * 配置中心Mapper接口
 * 
 * @author cq
 */
public interface RcConfigMapper 
{
    /**
     * 查询配置中心
     * 
     * @param id 配置中心主键
     * @return 配置中心
     */
    public RcConfig selectRcConfigById(Long id);

    /**
     * 查询配置中心列表
     * 
     * @param rcConfig 配置中心
     * @return 配置中心集合
     */
    public List<RcConfig> selectRcConfigList(RcConfig rcConfig);

    /**
     * 新增配置中心
     * 
     * @param rcConfig 配置中心
     * @return 结果
     */
    public int insertRcConfig(RcConfig rcConfig);

    /**
     * 修改配置中心
     * 
     * @param rcConfig 配置中心
     * @return 结果
     */
    public int updateRcConfig(RcConfig rcConfig);

    /**
     * 删除配置中心
     * 
     * @param id 配置中心主键
     * @return 结果
     */
    public int deleteRcConfigById(Long id);

    /**
     * 批量删除配置中心
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteRcConfigByIds(Long[] ids);

    /**
     * 校验配置键是否唯一
     * 
     * @param envId 环境ID
     * @param projectId 项目ID
     * @param configKey 配置键
     * @return 结果
     */
    public RcConfig checkConfigKeyUnique(@Param("envId") Long envId, @Param("projectId") Long projectId, @Param("configKey") String configKey);
}
