package com.cq.panel.admin.server.service.batch;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.Map;

@HttpExchange("/api/v1/batch")
public interface ProxyApi
{
    @PostExchange("/tasks/{taskId}/start")
    Map<String, Object> startTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request);

    @PutExchange("/tasks/{taskId}/pause")
    Map<String, Object> pauseTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request);

    @PutExchange("/tasks/{taskId}/resume")
    Map<String, Object> resumeTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request);

    @PutExchange("/tasks/{taskId}/cancel")
    Map<String, Object> cancelTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request);
}
