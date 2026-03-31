package com.cq.agent.dto;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

/**
 * Proxy服务端返回的API响应格式
 * 与proxy的ApiResponse格式匹配
 * 
 * @author cq
 */
public class ProxyApiResponse<T> {
    
    private int code;
    private String message;
    private T data;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return code == 200;
    }

    /**
     * 自定义反序列化器，正确处理泛型类型
     */
    public static class ProxyApiResponseDeserializer<T> implements JsonDeserializer<ProxyApiResponse<T>> {
        private final Type dataType;

        public ProxyApiResponseDeserializer(Type type) {
            // 从ProxyApiResponse<T>中提取T的实际类型
            this.dataType = extractDataType(type);
        }

        private Type extractDataType(Type type) {
            if (type instanceof java.lang.reflect.ParameterizedType) {
                java.lang.reflect.ParameterizedType pType = (java.lang.reflect.ParameterizedType) type;
                Type[] actualTypeArguments = pType.getActualTypeArguments();
                if (actualTypeArguments.length > 0) {
                    return actualTypeArguments[0];
                }
            }
            return Object.class;
        }

        @Override
        public ProxyApiResponse<T> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
                throws JsonParseException {
            ProxyApiResponse<T> response = new ProxyApiResponse<>();
            
            if (json.isJsonObject()) {
                JsonObject jsonObject = json.getAsJsonObject();
                
                if (jsonObject.has("code")) {
                    response.setCode(jsonObject.get("code").getAsInt());
                }
                
                // 优先使用message字段，如果不存在则使用msg字段
                if (jsonObject.has("message")) {
                    response.setMessage(jsonObject.get("message").getAsString());
                } else if (jsonObject.has("msg")) {
                    response.setMessage(jsonObject.get("msg").getAsString());
                }
                
                if (jsonObject.has("data")) {
                    JsonElement dataElement = jsonObject.get("data");
                    if (!dataElement.isJsonNull()) {
                        // 使用正确的类型反序列化data字段
                        @SuppressWarnings("unchecked")
                        T data = (T) context.deserialize(dataElement, dataType);
                        response.setData(data);
                    }
                }
            }
            
            return response;
        }
    }

    @Override
    public String toString() {
        return "ProxyApiResponse{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}