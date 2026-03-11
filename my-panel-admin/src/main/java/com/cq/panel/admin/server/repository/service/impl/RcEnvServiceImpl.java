package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import com.cq.panel.admin.server.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cq.panel.admin.server.repository.mapper.RcEnvMapper;
import com.cq.panel.admin.server.repository.domain.RcEnv;
import com.cq.panel.admin.server.repository.service.IRcEnvService;
import com.cq.panel.admin.server.common.constant.UserConstants;
import com.cq.panel.admin.server.common.utils.StringUtils;

/**
 * 环境管理Service业务层处理
 * 
 * @author cq
 */
@Service
public class RcEnvServiceImpl implements IRcEnvService 
{
    @Autowired
    private RcEnvMapper rcEnvMapper;

    /**
     * 查询环境管理
     * 
     * @param id 环境管理主键
     * @return 环境管理
     */
    @Override
    public RcEnv selectRcEnvById(Long id)
    {
        return rcEnvMapper.selectRcEnvById(id);
    }

    /**
     * 查询环境管理列表
     * 
     * @param rcEnv 环境管理
     * @return 环境管理
     */
    @Override
    public List<RcEnv> selectRcEnvList(RcEnv rcEnv)
    {
        return rcEnvMapper.selectRcEnvList(rcEnv);
    }

    /**
     * 新增环境管理
     * 
     * @param rcEnv 环境管理
     * @return 结果
     */
    @Override
    public int insertRcEnv(RcEnv rcEnv)
    {
        rcEnv.setCreateTime(DateUtils.getNowDate());
        return rcEnvMapper.insertRcEnv(rcEnv);
    }

    /**
     * 修改环境管理
     * 
     * @param rcEnv 环境管理
     * @return 结果
     */
    @Override
    public int updateRcEnv(RcEnv rcEnv)
    {
        rcEnv.setUpdateTime(DateUtils.getNowDate());
        return rcEnvMapper.updateRcEnv(rcEnv);
    }

    /**
     * 批量删除环境管理
     * 
     * @param ids 需要删除的环境管理主键
     * @return 结果
     */
    @Override
    public int deleteRcEnvByIds(Long[] ids)
    {
        return rcEnvMapper.deleteRcEnvByIds(ids);
    }

    /**
     * 删除环境管理信息
     * 
     * @param id 环境管理主键
     * @return 结果
     */
    @Override
    public int deleteRcEnvById(Long id)
    {
        return rcEnvMapper.deleteRcEnvById(id);
    }

    /**
     * 校验环境名称是否唯一
     * 
     * @param rcEnv 环境信息
     * @return 结果
     */
    @Override
    public boolean checkEnvNameUnique(RcEnv rcEnv)
    {
        Long id = StringUtils.isNull(rcEnv.getId()) ? -1L : rcEnv.getId();
        RcEnv info = rcEnvMapper.checkEnvNameUnique(rcEnv.getEnvName());
        if (StringUtils.isNotNull(info) && info.getId().longValue() != id.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }
}
