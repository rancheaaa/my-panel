package com.cq.proxy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cq.proxy.api.dto.ConfigUpdateRequest;
import com.cq.proxy.api.dto.ConfigValueResponse;
import com.cq.proxy.exception.BusinessException;
import com.cq.proxy.repository.entity.RcConfig;
import com.cq.proxy.repository.mapper.RcConfigMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

  @Mock
  private RcDictionaryService dictionaryService;

  @Mock
  private RcConfigMapper configMapper;

  private ConfigService service(Clock clock) {
    return new ConfigService(
        dictionaryService,
        configMapper,
        clock,
        Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(300, java.util.concurrent.TimeUnit.SECONDS));
  }

  @Test
  void getConfig_validatesParams() {
    ConfigService service = service(Clock.systemUTC());
    assertThrows(BusinessException.class, () -> service.getConfig(null, "dev", "svc"));
    assertThrows(BusinessException.class, () -> service.getConfig("k", null, "svc"));
    assertThrows(BusinessException.class, () -> service.getConfig("k", "dev", null));
  }

  @Test
  void getConfig_returnsAndCaches() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    ConfigService service = service(clock);

    when(dictionaryService.getOrCreateEnvId("dev")).thenReturn(1L);
    when(dictionaryService.getOrCreateProjectId("order-service")).thenReturn(2L);

    RcConfig cfg = new RcConfig();
    cfg.setConfigKey("a.b");
    cfg.setConfigValue("v1");
    cfg.setConfigDesc("d");
    when(configMapper.selectOne(any())).thenReturn(cfg);

    ConfigValueResponse first = service.getConfig("a.b", "dev", "order-service");
    assertEquals("v1", first.configValue());

    ConfigValueResponse second = service.getConfig("a.b", "dev", "order-service");
    assertEquals("v1", second.configValue());

    verify(configMapper, times(1)).selectOne(any());
  }

  @Test
  void updateConfig_insertsWhenMissing() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    ConfigService service = service(clock);

    when(dictionaryService.getOrCreateEnvId("dev")).thenReturn(1L);
    when(dictionaryService.getOrCreateProjectId("order-service")).thenReturn(2L);
    when(configMapper.selectOne(any())).thenReturn(null);

    ConfigUpdateRequest req = new ConfigUpdateRequest("dev", "order-service", "v2", "desc");
    ConfigValueResponse updated = service.updateConfig("a.b", req);
    assertEquals("v2", updated.configValue());

    verify(configMapper, times(1)).insert(any(RcConfig.class));
    verify(configMapper, never()).updateById(any(RcConfig.class));
  }

  @Test
  void updateConfig_updatesWhenExists() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    ConfigService service = service(clock);

    when(dictionaryService.getOrCreateEnvId("dev")).thenReturn(1L);
    when(dictionaryService.getOrCreateProjectId("order-service")).thenReturn(2L);

    RcConfig existing = new RcConfig();
    existing.setId(1L);
    existing.setEnvId(1L);
    existing.setProjectId(2L);
    existing.setConfigKey("a.b");
    existing.setConfigValue("v1");
    existing.setUpdateTime(LocalDateTime.ofInstant(Instant.parse("2025-12-31T23:00:00Z"), ZoneOffset.UTC));
    when(configMapper.selectOne(any())).thenReturn(existing);

    ConfigUpdateRequest req = new ConfigUpdateRequest("dev", "order-service", "v3", "d3");
    ConfigValueResponse updated = service.updateConfig("a.b", req);
    assertEquals("v3", updated.configValue());

    verify(configMapper, times(1)).updateById(any(RcConfig.class));
    verify(configMapper, never()).insert(any(RcConfig.class));
  }
}

