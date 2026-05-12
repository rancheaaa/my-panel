package com.cq.proxy.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ProxyBeansConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
  }

  @Bean
  public Caffeine<Object, Object> caffeine(ProxyCacheProperties properties) {
    return Caffeine.newBuilder()
        .maximumSize(properties.maximumSize())
        .expireAfterWrite(properties.expireAfterWriteSeconds(), TimeUnit.SECONDS);
  }

  @Bean
  public RestTemplate restTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(5000);
    return new RestTemplate(factory);
  }
}
