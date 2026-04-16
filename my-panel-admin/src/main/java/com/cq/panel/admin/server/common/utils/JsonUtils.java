package com.cq.panel.admin.server.common.utils;

import com.cq.panel.admin.server.filter.PropertyPreExcludeFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON工具类（基于Jackson）
 * 
 * @author cq
 */
public class JsonUtils
{
    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);

    private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    static
    {
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    public static com.fasterxml.jackson.databind.ObjectMapper getObjectMapper()
    {
        return objectMapper;
    }

    public static String toJSONString(Object object)
    {
        try
        {
            return objectMapper.writeValueAsString(object);
        }
        catch (Exception e)
        {
            log.error("JSON序列化失败", e);
            return "{}";
        }
    }

    public static String toJSONString(Object object, PropertyPreExcludeFilter filter)
    {
        try
        {
            if (filter == null || filter.getExcludes().isEmpty())
            {
                return objectMapper.writeValueAsString(object);
            }
            return objectMapper.writeValueAsString(object);
        }
        catch (Exception e)
        {
            log.error("JSON序列化失败", e);
            return "{}";
        }
    }

    public static <T> T parseObject(String json, Class<T> clazz)
    {
        try
        {
            return objectMapper.readValue(json, clazz);
        }
        catch (Exception e)
        {
            log.error("JSON反序列化失败", e);
            return null;
        }
    }

    public static String toPrettyJSONString(Object object)
    {
        try
        {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsString(object);
        }
        catch (Exception e)
        {
            log.error("JSON格式化输出失败", e);
            return "{}";
        }
    }
}