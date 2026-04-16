package com.cq.panel.admin.server.config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Redis使用Jackson序列化
 * 
 * @author cq
 */
public class Jackson2JsonRedisSerializer<T> implements RedisSerializer<T>
{
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private final ObjectMapper objectMapper;
    private final JavaType javaType;

    public Jackson2JsonRedisSerializer(ObjectMapper objectMapper, Class<T> clazz)
    {
        this.objectMapper = objectMapper;
        this.javaType = TypeFactory.defaultInstance().constructType(clazz);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
    }

    @Override
    public byte[] serialize(T t) throws SerializationException
    {
        if (t == null)
        {
            return new byte[0];
        }
        try
        {
            return objectMapper.writeValueAsBytes(t);
        }
        catch (Exception ex)
        {
            throw new SerializationException("Could not serialize: " + ex.getMessage(), ex);
        }
    }

    @Override
    public T deserialize(byte[] bytes) throws SerializationException
    {
        if (bytes == null || bytes.length <= 0)
        {
            return null;
        }
        try
        {
            return objectMapper.readValue(bytes, javaType);
        }
        catch (Exception ex)
        {
            throw new SerializationException("Could not deserialize: " + ex.getMessage(), ex);
        }
    }
}