package com.cq.proxy.service.batch;

import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

@HttpExchange("/api/internal/batch")
public interface AgentApi
{
    @PostExchange("/scan")
    Map<String, Object> scan(Map<String, Object> request);

    @PostExchange("/dispatch")
    Map<String, Object> dispatch(Map<String, Object> request);
}
