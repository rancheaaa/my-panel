package com.cq.panel.admin.server.web.controller.monitor;

import com.cq.panel.admin.server.web.domain.dto.monitor.SysUserOnlineQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysUserOnlineVO;
import com.cq.panel.admin.server.web.converter.monitor.SysUserOnlineConverter;
import com.github.pagehelper.PageInfo;
import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.constant.CacheConstants;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.repository.domain.SysUserOnline;
import com.cq.panel.admin.server.repository.service.ISysUserOnlineService;
import com.cq.panel.admin.server.web.service.cache.CacheService;
import com.cq.panel.admin.server.web.domain.model.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 在线用户监控
 * 
 * @author cq
 */
@Tag(name = "在线用户监控", description = "在线用户监控与管理接口")
@RestController
@RequestMapping("/monitor/online")
public class SysUserOnlineController extends BaseController
{
    @Autowired
    private ISysUserOnlineService userOnlineService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private SysUserOnlineConverter userOnlineConverter;

    @Operation(summary = "查询在线用户列表", description = "获取当前在线用户列表，支持分页和条件查询")
    @PreAuthorize("@ss.hasPermi('monitor:online:list')")
    @GetMapping("/list")
    public Result<PageVO<SysUserOnlineVO>> list(@Parameter(description = "查询条件") SysUserOnlineQueryDTO query)
    {
        Collection<String> keys = cacheService.keys(CacheConstants.LOGIN_TOKEN_KEY + "*");
        List<SysUserOnline> userOnlineList = new ArrayList<SysUserOnline>();
        for (String key : keys)
        {
            LoginUser user = cacheService.get(key);
            if (StringUtils.isNotEmpty(query.getIpaddr()) && StringUtils.isNotEmpty(query.getUserName()))
            {
                if (StringUtils.equals(query.getIpaddr(), user.getIpaddr()) && StringUtils.equals(query.getUserName(), user.getUsername()))
                {
                    userOnlineList.add(userOnlineService.selectOnlineByInfo(query.getIpaddr(), query.getUserName(), user));
                }
            }
            else if (StringUtils.isNotEmpty(query.getIpaddr()))
            {
                if (StringUtils.equals(query.getIpaddr(), user.getIpaddr()))
                {
                    userOnlineList.add(userOnlineService.selectOnlineByIpaddr(query.getIpaddr(), user));
                }
            }
            else if (StringUtils.isNotEmpty(query.getUserName()) && StringUtils.isNotNull(user.getUser()))
            {
                if (StringUtils.equals(query.getUserName(), user.getUsername()))
                {
                    userOnlineList.add(userOnlineService.selectOnlineByUserName(query.getUserName(), user));
                }
            }
            else
            {
                userOnlineList.add(userOnlineService.loginUserToUserOnline(user));
            }
        }
        Collections.reverse(userOnlineList);
        userOnlineList.removeAll(Collections.singleton(null));
        List<SysUserOnlineVO> voList = userOnlineConverter.toVOList(userOnlineList);
        return Result.success(new PageVO<>(voList, new PageInfo(userOnlineList).getTotal()));
    }

    @Operation(summary = "强退用户", description = "强制退出指定用户的会话")
    @PreAuthorize("@ss.hasPermi('monitor:online:forceLogout')")
    @Log(title = "在线用户", businessType = BusinessType.FORCE)
    @DeleteMapping("/{tokenId}")
    public Result<Void> forceLogout(@Parameter(description = "会话编号", required = true) @PathVariable String tokenId)
    {
        cacheService.delete(CacheConstants.LOGIN_TOKEN_KEY + tokenId);
        return Result.success();
    }
}





