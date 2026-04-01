package com.cq.panel.common.loadbalancer;

import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于Apache HttpClient 5的HTTP客户端实现
 * 提供更强大的功能和更好的性能
 */
public class ApacheHttpClient implements HttpClient {
    
    private static final Logger logger = LoggerFactory.getLogger(ApacheHttpClient.class);
    
    private CloseableHttpClient httpClient;
    private int connectTimeout;
    private int readTimeout;

    public ApacheHttpClient() {
        this(5000, 10000); // 默认5秒连接超时，10秒读取超时
    }

    public ApacheHttpClient(int connectTimeout, int readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.httpClient = createHttpClient();
    }

    @Override
    public <T> HttpResponse<T> get(String url, Type responseType) {
        return executeRequest(new HttpGet(url), null, responseType, new HashMap<>());
    }

    @Override
    public <T> HttpResponse<T> get(String url, Map<String, String> headers, Type responseType) {
        HttpGet request = new HttpGet(url);
        setHeaders(request, headers);
        return executeRequest(request, null, responseType, headers);
    }

    @Override
    public <T> HttpResponse<T> post(String url, Object body, Type responseType) {
        return executeRequest(new HttpPost(url), body, responseType, new HashMap<>());
    }

    @Override
    public <T> HttpResponse<T> post(String url, Object body, Map<String, String> headers, Type responseType) {
        HttpPost request = new HttpPost(url);
        setHeaders(request, headers);
        return executeRequest(request, body, responseType, headers);
    }

    @Override
    public <T> HttpResponse<T> put(String url, Object body, Type responseType) {
        return executeRequest(new HttpPut(url), body, responseType, new HashMap<>());
    }

    @Override
    public <T> HttpResponse<T> put(String url, Object body, Map<String, String> headers, Type responseType) {
        HttpPut request = new HttpPut(url);
        setHeaders(request, headers);
        return executeRequest(request, body, responseType, headers);
    }

    @Override
    public <T> HttpResponse<T> delete(String url, Type responseType) {
        return executeRequest(new HttpDelete(url), null, responseType, new HashMap<>());
    }

    @Override
    public <T> HttpResponse<T> delete(String url, Map<String, String> headers, Type responseType) {
        HttpDelete request = new HttpDelete(url);
        setHeaders(request, headers);
        return executeRequest(request, null, responseType, headers);
    }

    @Override
    public <T> HttpResponse<T> execute(String method, String url, Object body, Map<String, String> headers, Type responseType) {
        HttpUriRequestBase request = switch (method.toUpperCase()) {
            case "GET" -> new HttpGet(url);
            case "POST" -> new HttpPost(url);
            case "PUT" -> new HttpPut(url);
            case "DELETE" -> new HttpDelete(url);
            case "PATCH" -> new HttpPatch(url);
            case "HEAD" -> new HttpHead(url);
            case "OPTIONS" -> new HttpOptions(url);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };

        setHeaders(request, headers);
        return executeRequest(request, body, responseType, headers);
    }

    @Override
    public void setConnectTimeout(int timeout) {
        this.connectTimeout = timeout;
        // 重新创建HttpClient以应用新的超时设置
        close();
        this.httpClient = createHttpClient();
    }

    @Override
    public void setReadTimeout(int timeout) {
        this.readTimeout = timeout;
        // 重新创建HttpClient以应用新的超时设置
        close();
        this.httpClient = createHttpClient();
    }

    @Override
    public int getConnectTimeout() {
        return connectTimeout;
    }

    @Override
    public int getReadTimeout() {
        return readTimeout;
    }

    /**
     * 执行HTTP请求
     */
    private <T> HttpResponse<T> executeRequest(HttpUriRequestBase request, Object body, 
                                               Type responseType, Map<String, String> headers) {
        try {
            // 设置请求体（如果是POST、PUT或PATCH请求）
            if (body != null && (request instanceof HttpPost || request instanceof HttpPut || request instanceof HttpPatch)) {
                String requestBody = body instanceof String ? (String) body : body.toString();
                
                // 根据Content-Type设置实体
                String contentType = headers.get("Content-Type");
                if (contentType != null && contentType.contains("application/json")) {
                    StringEntity entity = new StringEntity(requestBody, ContentType.APPLICATION_JSON);
                    request.setEntity(entity);
                } else if (contentType != null && contentType.contains("application/xml")) {
                    StringEntity entity = new StringEntity(requestBody, ContentType.APPLICATION_XML);
                    request.setEntity(entity);
                } else if (contentType != null && contentType.contains("text/plain")) {
                    StringEntity entity = new StringEntity(requestBody, ContentType.TEXT_PLAIN);
                    request.setEntity(entity);
                } else {
                    // 默认使用JSON
                    StringEntity entity = new StringEntity(requestBody, ContentType.APPLICATION_JSON);
                    request.setEntity(entity);
                }
            }

            // 执行请求
            return httpClient.execute(request, response -> {
                int statusCode = response.getCode();
                
                // 获取响应头
                Map<String, String> responseHeaders = new HashMap<>();
                for (org.apache.hc.core5.http.Header header : response.getHeaders()) {
                    responseHeaders.put(header.getName(), header.getValue());
                }

                // 获取响应体
                String responseBody = response.getEntity() != null ? 
                        EntityUtils.toString(response.getEntity()) : "";

                // 使用智能反序列化器转换响应体类型
                T responseObj = ResponseDeserializer.deserialize(responseBody, responseHeaders, responseType);
                return new HttpResponse<>(responseObj, statusCode, responseHeaders);
            });

        } catch (Exception e) {
            String uriString = "unknown";
            try {
                uriString = request.getUri().toString();
            } catch (Exception uriException) {
                // 忽略URI获取异常
            }
            logger.error("HTTP request failed: {} {}", request.getMethod(), uriString, e);
            return new HttpResponse<>(null, 500);
        }
    }

    /**
     * 设置请求头
     */
    private void setHeaders(HttpUriRequestBase request, Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(request::setHeader);
        }
    }

    /**
     * 创建HttpClient实例
     */
    private CloseableHttpClient createHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeout))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeout))
                .build();

        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setUserAgent("ApacheHttpClient/5.0")
                .build();
    }

    /**
     * 关闭HttpClient
     */
    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (Exception e) {
            logger.error("Failed to close HttpClient", e);
        }
    }
}