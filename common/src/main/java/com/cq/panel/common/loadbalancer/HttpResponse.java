package com.cq.panel.common.loadbalancer;

import java.util.HashMap;
import java.util.Map;

/**
 * 简化的HTTP响应实体类
 * 替代Spring的ResponseEntity
 */
public class HttpResponse<T> {
    private final T body;
    private final int statusCode;
    private final Map<String, String> headers;

    public HttpResponse(T body, int statusCode) {
        this(body, statusCode, new HashMap<>());
    }

    public HttpResponse(T body, int statusCode, Map<String, String> headers) {
        this.body = body;
        this.statusCode = statusCode;
        this.headers = new HashMap<>(headers);
    }

    public T getBody() {
        return body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, String> getHeaders() {
        return new HashMap<>(headers);
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    public static <T> HttpResponse<T> ok(T body) {
        return new HttpResponse<>(body, 200);
    }

    public static <T> HttpResponse<T> created(T body) {
        return new HttpResponse<>(body, 201);
    }

    public static <T> HttpResponse<T> noContent() {
        return new HttpResponse<>(null, 204);
    }

    public static <T> HttpResponse<T> badRequest(T body) {
        return new HttpResponse<>(body, 400);
    }

    public static <T> HttpResponse<T> notFound(T body) {
        return new HttpResponse<>(body, 404);
    }

    public static <T> HttpResponse<T> serverError(T body) {
        return new HttpResponse<>(body, 500);
    }

    @Override
    public String toString() {
        return "HttpResponse{" +
                "statusCode=" + statusCode +
                ", body=" + body +
                ", headers=" + headers +
                '}';
    }
}