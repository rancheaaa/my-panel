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
    Instant lastHeartbeat
) {
    /**
     * Instant类型的自定义序列化器
     */
    public static class InstantTypeAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

        @Override
        public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(FORMATTER.format(src));
        }

        @Override
        public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
                throws JsonParseException {
            if (json.isJsonNull()) {
                return null;
            }
            String instantString = json.getAsString();
            if (instantString == null || instantString.isEmpty()) {
                return null;
            }
            try {
                return Instant.parse(instantString);
            } catch (Exception e) {
                throw new JsonParseException("Failed to parse Instant: " + instantString, e);
            }
        }
    }
}