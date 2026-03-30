package com.cq.panel.admin.server.repository.mapper;

import java.util.List;
import com.cq.panel.admin.server.repository.domain.RcAccessToken;

/**
 * AccessToken管理Mapper接口
 * 
 * @author cq
 */
public interface RcAccessTokenMapper 
{
    /**
     * 查询AccessToken管理
     * 
     * @param id AccessToken管理主键
     * @return AccessToken管理
     */
    RcAccessToken selectRcAccessTokenById(Long id);

    /**
     * 查询AccessToken管理列表
     * 
     * @param rcAccessToken AccessToken管理
     * @return AccessToken管理集合
     */
    List<RcAccessToken> selectRcAccessTokenList(RcAccessToken rcAccessToken);

    /**
     * 新增AccessToken管理
     * 
     * @param rcAccessToken AccessToken管理
     * @return 结果
     */
    int insertRcAccessToken(RcAccessToken rcAccessToken);

    /**
     * 修改AccessToken管理
     * 
     * @param rcAccessToken AccessToken管理
     * @return 结果
     */
    int updateRcAccessToken(RcAccessToken rcAccessToken);

    /**
     * 删除AccessToken管理
     * 
     * @param id AccessToken管理主键
     * @return 结果
     */
    int deleteRcAccessTokenById(Long id);

    /**
     * 批量删除AccessToken管理
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteRcAccessTokenByIds(Long[] ids);

    /**
     * 校验Token值是否唯一
     * 
     * @param tokenValue Token值
     * @return 结果
     */
    RcAccessToken checkTokenValueUnique(String tokenValue);
}
