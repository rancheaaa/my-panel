package com.cq.panel.admin.server.service.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ProxyApiClient
{
    private static final Logger logger = LoggerFactory.getLogger(ProxyApiClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${app.proxy.base-url:http://localhost:9876}")
    private String proxyBaseUrl;

    public Map<String, Object> startTask(Long taskId, Map<String, Object> request)
    {
        return postToProxy("/api/v1/batch/tasks/" + taskId + "/start", request);
    }

    public Map<String, Object> pauseTask(Long taskId, String sourceAgentApiUrl)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceAgentApiUrl", sourceAgentApiUrl);
        return putToProxy("/api/v1/batch/tasks/" + taskId + "/pause", body);
    }

    public Map<String, Object> resumeTask(Long taskId, String sourceAgentApiUrl)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceAgentApiUrl", sourceAgentApiUrl);
        return putToProxy("/api/v1/batch/tasks/" + taskId + "/resume", body);
    }

    public Map<String, Object> cancelTask(Long taskId, String sourceAgentApiUrl)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceAgentApiUrl", sourceAgentApiUrl);
        return putToProxy("/api/v1/batch/tasks/" + taskId + "/cancel", body);
    }

    private Map<String, Object> putToProxy(String path, Object body)
    {
        return sendToProxy("PUT", path, body);
    }

    private Map<String, Object> postToProxy(String path, Object body)
    {
        return sendToProxy("POST", path, body);
    }

    private Map<String, Object> sendToProxy(String method, String path, Object body)
    {
        try
        {
            String json = OBJECT_MAPPER.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(proxyBaseUrl + path))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60));

            if ("POST".equals(method))
            {
                builder.POST(HttpRequest.BodyPublishers.ofString(json));
            }
            else
            {
                builder.PUT(HttpRequest.BodyPublishers.ofString(json));
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400)
            {
                logger.error("Proxy API call failed: {} {}, status={}, body={}", method, path, response.statusCode(), response.body());
                throw new RuntimeException("Proxy调用失败: status=" + response.statusCode());
            }
            return OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {});
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            logger.error("Proxy API call error: {} {}, error={}", method, path, e.getMessage(), e);
            throw new RuntimeException("Proxy调用异常: " + e.getMessage(), e);
        }
    }
}
