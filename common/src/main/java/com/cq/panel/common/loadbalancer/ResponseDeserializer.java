package com.cq.panel.common.loadbalancer;

import com.google.gson.Gson;
import java.util.Map;
import java.util.HashMap;

/**
 * 智能响应反序列化器
 * 基于HTTP响应头的Content-Type来决定如何反序列化响应体
 */
public class ResponseDeserializer {
    
    private static final Map<String, ContentTypeHandler> CONTENT_TYPE_HANDLERS = new HashMap<>();
    
    static {
        // JSON处理
        CONTENT_TYPE_HANDLERS.put("application/json", new JsonContentTypeHandler());
        CONTENT_TYPE_HANDLERS.put("application/json;charset=utf-8", new JsonContentTypeHandler());
        CONTENT_TYPE_HANDLERS.put("application/json; charset=utf-8", new JsonContentTypeHandler());
        
        // XML处理
        CONTENT_TYPE_HANDLERS.put("application/xml", new TextContentTypeHandler());
        CONTENT_TYPE_HANDLERS.put("text/xml", new TextContentTypeHandler());
        CONTENT_TYPE_HANDLERS.put("application/xml;charset=utf-8", new TextContentTypeHandler());
        
        // 文本处理
        CONTENT_TYPE_HANDLERS.put("text/plain", new TextContentTypeHandler());
        CONTENT_TYPE_HANDLERS.put("text/html", new TextContentTypeHandler());
        CONTENT_TYPE_HANDLERS.put("text/css", new TextContentTypeHandler());
        CONTENT_TYPE_HANDLERS.put("text/javascript", new TextContentTypeHandler());
        
        // 二进制处理
        CONTENT_TYPE_HANDLERS.put("application/octet-stream", new BinaryContentTypeHandler());
        CONTENT_TYPE_HANDLERS.put("application/pdf", new BinaryContentTypeHandler());
        CONTENT_TYPE_HANDLERS.put("image/jpeg", new BinaryContentTypeHandler());
        CONTENT_TYPE_HANDLERS.put("image/png", new BinaryContentTypeHandler());
        CONTENT_TYPE_HANDLERS.put("image/gif", new BinaryContentTypeHandler());
    }
    
    /**
     * 基于Content-Type智能反序列化响应体
     * 
     * @param responseBody 响应体字符串
     * @param contentType Content-Type头
     * @param responseType 目标类型
     * @return 反序列化后的对象
     */
    public static <T> T deserialize(String responseBody, String contentType, Class<T> responseType) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }
        
        // 获取Content-Type处理器
        ContentTypeHandler handler = getContentTypeHandler(contentType);
        
        // 使用处理器进行反序列化
        return handler.deserialize(responseBody, responseType);
    }
    
    /**
     * 基于HTTP头映射智能反序列化响应体
     * 
     * @param responseBody 响应体字符串
     * @param headers HTTP头映射
     * @param responseType 目标类型
     * @return 反序列化后的对象
     */
    public static <T> T deserialize(String responseBody, Map<String, String> headers, Class<T> responseType) {
        String contentType = headers != null ? headers.get("Content-Type") : null;
        return deserialize(responseBody, contentType, responseType);
    }
    
    /**
     * 获取Content-Type处理器
     */
    private static ContentTypeHandler getContentTypeHandler(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            // 默认使用文本处理器
            return new TextContentTypeHandler();
        }
        
        // 标准化Content-Type（移除空格，转为小写）
        String normalizedContentType = contentType.toLowerCase().replace(" ", "");
        
        // 查找匹配的处理器
        for (Map.Entry<String, ContentTypeHandler> entry : CONTENT_TYPE_HANDLERS.entrySet()) {
            if (normalizedContentType.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 如果没有找到匹配的处理器，默认使用文本处理器
        return new TextContentTypeHandler();
    }
    
    /**
     * Content-Type处理器接口
     */
    private interface ContentTypeHandler {
        <T> T deserialize(String responseBody, Class<T> responseType);
    }
    
    /**
     * JSON Content-Type处理器
     */
    private static class JsonContentTypeHandler implements ContentTypeHandler {
        @Override
        public <T> T deserialize(String responseBody, Class<T> responseType) {
            try {
                // 如果是String类型，直接返回
                if (responseType == String.class) {
                    return responseType.cast(responseBody);
                }
                
                // 使用Gson进行JSON反序列化
                Gson gson = new Gson();
                return gson.fromJson(responseBody, responseType);
            } catch (Exception e) {
                throw new RuntimeException("JSON反序列化失败: " + responseBody, e);
            }
        }
    }
    
    /**
     * 文本Content-Type处理器
     */
    private static class TextContentTypeHandler implements ContentTypeHandler {
        @Override
        public <T> T deserialize(String responseBody, Class<T> responseType) {
            // 如果是String类型，直接返回
            if (responseType == String.class) {
                return responseType.cast(responseBody);
            }
            
            // 对于其他类型，尝试使用JSON反序列化（很多文本API实际上返回JSON）
            try {
                Gson gson = new Gson();
                return gson.fromJson(responseBody, responseType);
            } catch (Exception e) {
                // 如果JSON反序列化失败，尝试其他方式
                if (responseType == Integer.class || responseType == int.class) {
                    return responseType.cast(Integer.parseInt(responseBody.trim()));
                } else if (responseType == Long.class || responseType == long.class) {
                    return responseType.cast(Long.parseLong(responseBody.trim()));
                } else if (responseType == Double.class || responseType == double.class) {
                    return responseType.cast(Double.parseDouble(responseBody.trim()));
                } else if (responseType == Boolean.class || responseType == boolean.class) {
                    return responseType.cast(Boolean.parseBoolean(responseBody.trim()));
                } else {
                    throw new RuntimeException("文本反序列化失败，无法将文本转换为类型: " + responseType.getName());
                }
            }
        }
    }
    
    /**
     * 二进制Content-Type处理器
     */
    private static class BinaryContentTypeHandler implements ContentTypeHandler {
        @Override
        public <T> T deserialize(String responseBody, Class<T> responseType) {
            // 二进制数据通常不适合直接反序列化为对象
            // 这里返回Base64编码的字符串或原始字节数组
            if (responseType == String.class) {
                // 返回Base64编码的字符串
                return responseType.cast(java.util.Base64.getEncoder().encodeToString(responseBody.getBytes()));
            } else if (responseType == byte[].class) {
                return responseType.cast(responseBody.getBytes());
            } else {
                throw new RuntimeException("二进制数据无法反序列化为类型: " + responseType.getName());
            }
        }
    }
}