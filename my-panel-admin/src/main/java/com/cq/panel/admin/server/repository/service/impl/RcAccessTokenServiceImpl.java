package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import com.cq.panel.admin.server.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cq.panel.admin.server.repository.mapper.RcAccessTokenMapper;
import com.cq.panel.admin.server.repository.domain.RcAccessToken;
import com.cq.panel.admin.server.repository.service.IRcAccessTokenService;
import com.cq.panel.admin.server.common.constant.UserConstants;
import com.cq.panel.admin.server.common.utils.StringUtils;

/**
 * AccessToken管理Service业务层处理
 * 
 * @author cq
 */
@Service
public class RcAccessTokenServiceImpl implements IRcAccessTokenService 
{
    @Autowired
    private RcAccessTokenMapper rcAccessTokenMapper;

    /**
     * 查询AccessToken管理
     * 
     * @param id AccessToken管理主键
     * @return AccessToken管理
     */
    @Override
    public RcAccessToken selectRcAccessTokenById(Long id)
    {
        return rcAccessTokenMapper.selectRcAccessTokenById(id);
    }

    /**
     * 查询AccessToken管理列表
     * 
     * @param rcAccessToken AccessToken管理
     * @return AccessToken管理
     */
    @Override
    public List<RcAccessToken> selectRcAccessTokenList(RcAccessToken rcAccessToken)
    {
        return rcAccessTokenMapper.selectRcAccessTokenList(rcAccessToken);
    }

    /**
     * 新增AccessToken管理
     * 
     * @param rcAccessToken AccessToken管理
     * @return 结果
     */
    @Override
    public int insertRcAccessToken(RcAccessToken rcAccessToken)
    {
        rcAccessToken.setCreateTime(DateUtils.getNowDate());
        return rcAccessTokenMapper.insertRcAccessToken(rcAccessToken);
    }

    /**
     * 修改AccessToken管理
     * 
     * @param rcAccessToken AccessToken管理
     * @return 结果
     */
    @Override
    public int updateRcAccessToken(RcAccessToken rcAccessToken)
    {
        rcAccessToken.setUpdateTime(DateUtils.getNowDate());
        return rcAccessTokenMapper.updateRcAccessToken(rcAccessToken);
    }

    /**
     * 批量删除AccessToken管理
     * 
     * @param ids 需要删除的AccessToken管理主键
     * @return 结果
     */
    @Override
    public int deleteRcAccessTokenByIds(Long[] ids)
    {
        return rcAccessTokenMapper.deleteRcAccessTokenByIds(ids);
    }

    /**
     * 删除AccessToken管理信息
     * 
     * @param id AccessToken管理主键
     * @return 结果
     */
    @Override
    public int deleteRcAccessTokenById(Long id)
    {
        return rcAccessTokenMapper.deleteRcAccessTokenById(id);
    }

    /**
     * 校验Token值是否唯一
     * 
     * @param rcAccessToken Token information
     * @return Result
     */
    @Override
    public boolean checkTokenValueUnique(RcAccessToken rcAccessToken)
    {
        Long id = StringUtils.isNull(rcAccessToken.getId()) ? -1L : rcAccessToken.getId();
        RcAccessToken info = rcAccessTokenMapper.checkTokenValueUnique(rcAccessToken.getTokenValue());
        if (StringUtils.isNotNull(info) && info.getId().longValue() != id.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 修改AccessToken状态
     * 
     * @param rcAccessToken AccessToken信息
     * @return 结果
     */
    @Override
    public int updateStatus(RcAccessToken rcAccessToken)
    {
        return rcAccessTokenMapper.updateRcAccessToken(rcAccessToken);
    }
}
