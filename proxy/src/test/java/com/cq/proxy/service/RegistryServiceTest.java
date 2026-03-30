package com.cq.proxy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cq.proxy.api.dto.ServiceInstance;
import com.cq.proxy.api.dto.ServiceRegisterRequest;
import com.cq.proxy.config.ProxyRegistryProperties;
import com.cq.proxy.exception.BusinessException;
import com.cq.proxy.repository.entity.RcNode;
import com.cq.proxy.repository.mapper.RcNodeMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistryServiceTest {

  @Mock
  private RcDictionaryService dictionaryService;

  @Mock
  private RcNodeMapper nodeMapper;

  private RegistryService service(Clock clock, long timeoutSeconds) {
    ProxyRegistryProperties props = new ProxyRegistryProperties(timeoutSeconds, 10);
    return new RegistryService(
        dictionaryService,
        nodeMapper,
        props,
        clock,
        Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(300, java.util.concurrent.TimeUnit.SECONDS));
  }

  @Test
  void register_validatesRequest() {
    RegistryService service = service(Clock.systemUTC(), 60);
    assertThrows(BusinessException.class, () -> service.register(null));
    assertThrows(BusinessException.class, () -> service.register(new ServiceRegisterRequest("svc", "dev", "", 8080)));
    assertThrows(BusinessException.class, () -> service.register(new ServiceRegisterRequest("svc", "dev", "127.0.0.1", 0)));
  }

  @Test
  void register_insertsWhenMissing() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    RegistryService service = service(clock, 60);

    when(dictionaryService.getOrCreateEnvId("dev")).thenReturn(1L);
    when(dictionaryService.getOrCreateProjectId("order-service")).thenReturn(2L);
    when(nodeMapper.selectOne(any())).thenReturn(null);

    service.register(new ServiceRegisterRequest("order-service", "dev", "127.0.0.1", 8080));

    verify(nodeMapper, times(1)).insert(any(RcNode.class));
    verify(nodeMapper, never()).updateById(any(RcNode.class));
  }

  @Test
  void register_updatesWhenExists() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    RegistryService service = service(clock, 60);

    when(dictionaryService.getOrCreateEnvId("dev")).thenReturn(1L);
    when(dictionaryService.getOrCreateProjectId("order-service")).thenReturn(2L);

    RcNode existing = new RcNode();
    existing.setId(10L);
    existing.setEnvId(1L);
    existing.setProjectId(2L);
    existing.setNodeIp("127.0.0.1");
    existing.setNodePort(8080);
    existing.setStatus("1");
    existing.setLastRefreshTime(LocalDateTime.ofInstant(Instant.parse("2025-12-31T23:00:00Z"), ZoneOffset.UTC));
    when(nodeMapper.selectOne(any())).thenReturn(existing);

    service.register(new ServiceRegisterRequest("order-service", "dev", "127.0.0.1", 8080));

    verify(nodeMapper, times(1)).updateById(any(RcNode.class));
    verify(nodeMapper, never()).insert(any(RcNode.class));
  }

  @Test
  void discover_returnsAndCaches() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    RegistryService service = service(clock, 60);

    when(dictionaryService.getOrCreateEnvId("dev")).thenReturn(1L);
    when(dictionaryService.getOrCreateProjectId("order-service")).thenReturn(2L);

    RcNode node = new RcNode();
    node.setNodeIp("10.0.0.1");
    node.setNodePort(8080);
    node.setStatus("0");
    node.setLastRefreshTime(LocalDateTime.ofInstant(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    when(nodeMapper.selectList(any())).thenReturn(List.of(node));

    List<ServiceInstance> first = service.discover("order-service", "dev");
    assertEquals(1, first.size());
    assertNotNull(first.getFirst().lastHeartbeat());

    List<ServiceInstance> second = service.discover("order-service", "dev");
    assertEquals(1, second.size());
    verify(nodeMapper, times(1)).selectList(any());
  }

  @Test
  void scanAndMarkOffline_updatesExpiredNodes() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:01:00Z"), ZoneOffset.UTC);
    RegistryService service = service(clock, 60);

    RcNode expired = new RcNode();
    expired.setId(1L);
    expired.setStatus("0");
    expired.setLastRefreshTime(LocalDateTime.ofInstant(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    when(nodeMapper.selectList(any())).thenReturn(List.of(expired));
    service.scanAndMarkOffline();

    verify(nodeMapper, times(1)).updateById(any(RcNode.class));
  }
}

