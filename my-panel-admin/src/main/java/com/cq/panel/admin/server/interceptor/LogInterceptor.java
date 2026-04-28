package com.cq.panel.admin.server.interceptor;

import com.cq.panel.admin.server.common.utils.JsonUtils;
import com.cq.panel.admin.server.common.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component
@SuppressWarnings("all")
public class LogInterceptor implements HandlerInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(LogInterceptor.class);
    private static final ThreadLocal<Long> startTime = new ThreadLocal<>();
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception
    {
        startTime.set(System.currentTimeMillis());
        
        String traceId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(TRACE_ID_KEY, traceId);
        request.setAttribute("traceId", traceId);
        
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String contentType = request.getContentType();
        
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements())
        {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        
        Map<String, String> params = ServletUtils.getParamMap(request);
        
        String requestBody = "";
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))
        {
            if (contentType != null && (contentType.contains("application/json") || contentType.contains("application/x-www-form-urlencoded")))
            {
                requestBody = com.cq.panel.admin.server.common.utils.http.HttpHelper.getBodyString(request);
            }
        }
        
        logger.trace("========== 请求开始 ==========");
        logger.trace("traceId: {}", traceId);
        logger.trace("请求方法: {}", method);
        logger.trace("请求URI: {}", uri);
        if (queryString != null && !queryString.isEmpty())
        {
            logger.trace("查询参数: {}", queryString);
        }
        logger.trace("Content-Type: {}", contentType);
        logger.trace("请求头: {}", JsonUtils.toJSONString(headers));
        if (!params.isEmpty())
        {
            logger.trace("请求参数: {}", JsonUtils.toJSONString(params));
        }
        if (!requestBody.isEmpty())
        {
            logger.trace("请求体: {}", requestBody);
        }
        logger.trace("远程地址: {}", request.getRemoteAddr());
        logger.trace("============================");
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception
    {
        String traceId = (String) request.getAttribute("traceId");
        long executeTime = System.currentTimeMillis() - startTime.get();
        
        String method = request.getMethod();
        String uri = request.getRequestURI();
        int status = response.getStatus();
        
        Map<String, String> responseHeaders = new HashMap<>();
        for (String headerName : response.getHeaderNames())
        {
            responseHeaders.put(headerName, response.getHeader(headerName));
        }
        
        logger.trace("========== 响应结束 ==========");
        logger.trace("traceId: {}", traceId);
        logger.trace("请求方法: {}", method);
        logger.trace("请求URI: {}", uri);
        logger.trace("响应状态: {}", status);
        logger.trace("响应头: {}", JsonUtils.toJSONString(responseHeaders));
        // todo 打印出报文响应体
        logger.trace("响应体: {}", "");
        logger.trace("执行时间: {} ms", executeTime);
        
        if (ex != null)
        {
            logger.trace("请求异常: ", ex);
        }
        
        logger.trace("============================");
        
        startTime.remove();
        MDC.remove(TRACE_ID_KEY);
    }
}