package com.cq.panel.admin.server.service.batch.util;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JSON序列化工具
 * 
 * 用于将BatchTransferTask实体序列化为JSON格式（用于Agent本地配置持久化），
 * 支持字段过滤、格式转换和特殊字符处理。
 */
@Component
public class BatchConfigSerializer {

    private final ObjectMapper objectMapper;
    
    public BatchConfigSerializer() {
        this.objectMapper = createObjectMapper();
    }
    
    public BatchConfigSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 序列化对象为JSON字符串
     */
    public <T> String serialize(T object) throws SerializationException {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new SerializationException("序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 序列化对象，只包含指定字段
     */
    public <T> String serialize(T object, List<String> includeFields) throws SerializationException {
        if (includeFields == null || includeFields.isEmpty()) {
            return serialize(object);
        }
        
        try {
            Map<String, Object> map = objectMapper.convertValue(object, new TypeReference<Map<String, Object>>() {});
            
            Map<String, Object> filteredMap = map.entrySet().stream()
                .filter(entry -> includeFields.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                
            return objectMapper.writeValueAsString(filteredMap);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new SerializationException("字段过滤序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 序列化对象，排除指定字段
     */
    public <T> String serializeExclude(T object, List<String> excludeFields) throws SerializationException {
        if (excludeFields == null || excludeFields.isEmpty()) {
            return serialize(object);
        }
        
        try {
            Map<String, Object> map = objectMapper.convertValue(object, new TypeReference<Map<String, Object>>() {});
            
            Set<String> excludeSet = new HashSet<>(excludeFields);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> filteredMap = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!excludeSet.contains(entry.getKey())) {
                    filteredMap.put(entry.getKey(), entry.getValue());
                }
            }
                
            return objectMapper.writeValueAsString(filteredMap);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new SerializationException("字段排除序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 反序列化JSON字符串为指定类型对象
     */
    public <T> T deserialize(String json, Class<T> clazz) throws DeserializationException {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (IOException e) {
            throw new DeserializationException("反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建ObjectMapper实例（支持日期时间格式化）
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * 序列化异常
     */
    public static class SerializationException extends RuntimeException {
        public SerializationException(String message) {
            super(message);
        }
        
        public SerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 反序列化异常
     */
    public static class DeserializationException extends RuntimeException {
        public DeserializationException(String message) {
            super(message);
        }
        
        public DeserializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
