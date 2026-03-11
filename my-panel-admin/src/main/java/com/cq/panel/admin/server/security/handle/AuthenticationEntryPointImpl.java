package com.cq.panel.admin.server.security.handle;

import java.io.IOException;
import java.io.Serializable;

import java.util.HashMap;
import java.util.Map;

import com.cq.panel.admin.server.common.constant.HttpStatus;
import com.cq.panel.admin.server.common.utils.ServletUtils;
import com.cq.panel.admin.server.common.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;

/**
 * 认证失败处理类 返回未授权
 * 
 * @author cq
 */
@Component
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint, Serializable
{
    private static final long serialVersionUID = -8970718410437077606L;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException
    {
        int code = HttpStatus.UNAUTHORIZED;
        String msg = StringUtils.format("请求访问：{}，认证失败，无法访问系统资源", request.getRequestURI());
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("msg", msg);
        ServletUtils.renderString(response, JSON.toJSONString(map));
    }
}
