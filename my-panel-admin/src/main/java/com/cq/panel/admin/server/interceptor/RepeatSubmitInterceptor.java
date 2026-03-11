package com.cq.panel.admin.server.interceptor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.cq.panel.admin.server.common.annotation.RepeatSubmit;
import com.cq.panel.admin.server.common.constant.HttpStatus;
import com.cq.panel.admin.server.common.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import com.alibaba.fastjson2.JSON;

/**
 * 防止重复提交拦截器
 *
 * @author cq
 */
@Component
public abstract class RepeatSubmitInterceptor implements HandlerInterceptor
{
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception
    {
        if (handler instanceof HandlerMethod)
        {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Method method = handlerMethod.getMethod();
            RepeatSubmit annotation = method.getAnnotation(RepeatSubmit.class);
            if (annotation != null)
            {
                if (this.isRepeatSubmit(request, annotation))
                {
                    Map<String, Object> map = new HashMap<>();
                    map.put("code", HttpStatus.ERROR);
                    map.put("msg", annotation.message());
                    ServletUtils.renderString(response, JSON.toJSONString(map));
                    return false;
                }
            }
            return true;
        }
        else
        {
            return true;
        }
    }

    /**
     * 验证是否重复提交由子类实现具体的防重复提交的规则
     *
     * @param request 请求信息
     * @param annotation 防重复注解参数
     * @return 结果
     * @throws Exception
     */
    public abstract boolean isRepeatSubmit(HttpServletRequest request, RepeatSubmit annotation);
}
