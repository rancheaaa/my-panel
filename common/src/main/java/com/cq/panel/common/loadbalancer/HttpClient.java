package com.cq.panel.common.loadbalancer;

import java.util.Map;

/**
 * HTTP客户端接口
 * 定义统一的HTTP请求方法
 */
public interface HttpClient {

    /**
     * 执行HTTP GET请求
     * 
     * @param url 请求URL
     * @param responseType 响应类型
     * @return HTTP响应
     */
    <T> HttpResponse<T> get(String url, Class<T> responseType);

    /**
     * 执行带HTTP头的GET请求
     * 
     * @param url 请求URL
     * @param headers HTTP头
     * @param responseType 响应类型
     * @return HTTP响应
     */
    <T> HttpResponse<T> get(String url, Map<String, String> headers, Class<T> responseType);

    /**
     * 执行HTTP POST请求
     * 
     * @param url 请求URL
     * @param body 请求体
     * @param responseType 响应类型
     * @return HTTP响应
     */
    <T> HttpResponse<T> post(String url, Object body, Class<T> responseType);

    /**
     * 执行带HTTP头的POST请求
     * 
     * @param url 请求URL
     * @param body 请求体
     * @param headers HTTP头
     * @param responseType 响应类型
     * @return HTTP响应
     */
    <T> HttpResponse<T> post(String url, Object body, Map<String, String> headers, Class<T> responseType);

    /**
     * 执行HTTP PUT请求
     * 
     * @param url 请求URL
     * @param body 请求体
     * @param responseType 响应类型
     * @return HTTP响应
     */
    <T> HttpResponse<T> put(String url, Object body, Class<T> responseType);

    /**
     * 执行带HTTP头的PUT请求
     * 
     * @param url 请求URL
     * @param body 请求体
     * @param headers HTTP头
     * @param responseType 响应类型
     * @return HTTP响应
     */
    <T> HttpResponse<T> put(String url, Object body, Map<String, String> headers, Class<T> responseType);

    /**
     * 执行HTTP DELETE请求
     * 
     * @param url 请求URL
     * @param responseType 响应类型
     * @return HTTP响应
     */
    <T> HttpResponse<T> delete(String url, Class<T> responseType);

    /**
     * 执行带HTTP头的DELETE请求
     * 
     * @param url 请求URL
     * @param headers HTTP头
     * @param responseType 响应类型
     * @return HTTP响应
     */
    <T> HttpResponse<T> delete(String url, Map<String, String> headers, Class<T> responseType);

    /**
     * 执行通用HTTP请求
     * 
     * @param method HTTP方法
     * @param url 请求URL
     * @param body 请求体
     * @param headers HTTP头
     * @param responseType 响应类型
     * @return HTTP响应
     */
    <T> HttpResponse<T> execute(String method, String url, Object body, Map<String, String> headers, Class<T> responseType);

    /**
     * 设置连接超时时间（毫秒）
     * 
     * @param timeout 超时时间
     */
    void setConnectTimeout(int timeout);

    /**
     * 设置读取超时时间（毫秒）
     * 
     * @param timeout 超时时间
     */
    void setReadTimeout(int timeout);

    /**
     * 获取连接超时时间（毫秒）
     * 
     * @return 连接超时时间
     */
    int getConnectTimeout();

    /**
     * 获取读取超时时间（毫秒）
     * 
     * @return 读取超时时间
     */
    int getReadTimeout();
}