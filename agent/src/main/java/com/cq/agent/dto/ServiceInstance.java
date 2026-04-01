package com.cq.agent.dto;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 服务实例
 * 与proxy的ServiceInstance格式匹配
 * 
 * @author cq
 */
public record ServiceInstance(
    String serviceName,
    String environment,
    String host,
    int port,
    boolean available,
    String lastHeartbeat
) {
}