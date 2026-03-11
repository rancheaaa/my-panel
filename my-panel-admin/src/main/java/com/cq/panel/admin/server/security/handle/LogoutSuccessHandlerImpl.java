package com.cq.panel.admin.server.security.handle;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.cq.panel.admin.server.common.constant.Constants;
import com.cq.panel.admin.server.common.constant.HttpStatus;
import com.cq.panel.admin.server.web.domain.model.LoginUser;
import com.cq.panel.admin.server.common.utils.MessageUtils;
import com.cq.panel.admin.server.common.utils.ServletUtils;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.manager.AsyncManager;
import com.cq.panel.admin.server.manager.factory.AsyncFactory;
import com.cq.panel.admin.server.web.service.TokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import com.alibaba.fastjson2.JSON;


/**
 * 自定义退出处理类 返回成功
 * 
 * @author cq
 */
@Configuration
public class LogoutSuccessHandlerImpl implements LogoutSuccessHandler
{
    @Autowired
    private TokenService tokenService;

    /**
     * 退出处理
     * 
     * @return
     */
    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException
    {
        LoginUser loginUser = tokenService.getLoginUser(request);
        if (StringUtils.isNotNull(loginUser))
        {
            String userName = loginUser.getUsername();
            // 删除用户缓存记录
            tokenService.delLoginUser(loginUser.getToken());
            // 记录用户退出日志
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(userName, Constants.LOGOUT, MessageUtils.message("user.logout.success")));
        }
        Map<String, Object> map = new HashMap<>();
        map.put("code", HttpStatus.SUCCESS);
        map.put("msg", MessageUtils.message("user.logout.success"));
        ServletUtils.renderString(response, JSON.toJSONString(map));
    }
}
