package com.cq.panel.common.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于Java标准库的HTTP客户端实现
 * 使用HttpURLConnection实现HttpClient接口
 */
public class SimpleHttpClient implements HttpClient {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleHttpClient.class);
    
    private int connectTimeout;
    private int readTimeout;

    public SimpleHttpClient() {
        this(5000, 10000); // 默认5秒连接超时，10秒读取超时
    }

    public SimpleHttpClient(int connectTimeout, int readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    @Override
    public <T> HttpResponse<T> get(String url, Type responseType) {
        return executeRequest("GET", url, null, responseType, new HashMap<>());
    }

    @Override
    public <T> HttpResponse<T> get(String url, Map<String, String> headers, Type responseType) {
        return executeRequest("GET", url, null, responseType, headers);
    }

    @Override
    public <T> HttpResponse<T> post(String url, Object body, Type responseType) {
        return executeRequest("POST", url, body, responseType, new HashMap<>());
    }

    @Override
    public <T> HttpResponse<T> post(String url, Object body, Map<String, String> headers, Type responseType) {
        return executeRequest("POST", url, body, responseType, headers);
    }

    @Override
    public <T> HttpResponse<T> put(String url, Object body, Type responseType) {
        return executeRequest("PUT", url, body, responseType, new HashMap<>());
    }

    @Override
    public <T> HttpResponse<T> put(String url, Object body, Map<String, String> headers, Type responseType) {
        return executeRequest("PUT", url, body, responseType, headers);
    }

    @Override
    public <T> HttpResponse<T> delete(String url, Type responseType) {
        return executeRequest("DELETE", url, null, responseType, new HashMap<>());
    }

    @Override
    public <T> HttpResponse<T> delete(String url, Map<String, String> headers, Type responseType) {
        return executeRequest("DELETE", url, null, responseType, headers);
    }

    @Override
    public <T> HttpResponse<T> execute(String method, String url, Object body, Map<String, String> headers, Type responseType) {
        return executeRequest(method, url, body, responseType, headers);
    }

    @Override
    public void setConnectTimeout(int timeout) {
        this.connectTimeout = timeout;
    }

    @Override
    public void setReadTimeout(int timeout) {
        this.readTimeout = timeout;
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
    private <T> HttpResponse<T> executeRequest(String method, String url, Object body, 
                                              Type responseType, Map<String, String> headers) {
        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URI(url).toURL();
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setDoInput(true);

            // 设置请求头
            headers.forEach(connection::setRequestProperty);
            
            // 如果是POST或PUT请求，设置请求体
            if (body != null && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
                connection.setDoOutput(true);
                
                // 如果没有指定Content-Type，默认使用JSON
                if (!headers.containsKey("Content-Type")) {
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                }
                
                String requestBody = body instanceof String ? (String) body : body.toString();
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            // 获取响应
            int statusCode = connection.getResponseCode();
            Map<String, String> responseHeaders = new HashMap<>();
            
            // 正确获取响应头，处理大小写不敏感
            for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
                String key = entry.getKey();
                if (key != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    // 将头名称转换为小写以确保一致性
                    String headerName = key.toLowerCase();
                    responseHeaders.put(headerName, String.join(", ", entry.getValue()));
                }
            }

            // 读取响应体
            StringBuilder responseBody = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line);
                }
            }

            // 使用智能反序列化器转换响应体类型
            T response = ResponseDeserializer.deserialize(responseBody.toString(), responseHeaders, responseType);
            return new HttpResponse<>(response, statusCode, responseHeaders);

        } catch (Exception e) {
            logger.error("HTTP request failed: {} {}", method, url, e);
            return new HttpResponse<>(null, 500);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}