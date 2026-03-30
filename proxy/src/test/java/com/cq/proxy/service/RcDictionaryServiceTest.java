package com.cq.proxy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.cq.proxy.exception.BusinessException;
import com.cq.proxy.repository.entity.RcEnv;
import com.cq.proxy.repository.entity.RcProject;
import com.cq.proxy.repository.mapper.RcEnvMapper;
import com.cq.proxy.repository.mapper.RcProjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RcDictionaryServiceTest {

  @Mock
  private RcEnvMapper envMapper;

  @Mock
  private RcProjectMapper projectMapper;

  private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  private RcDictionaryService service() {
    return new RcDictionaryService(envMapper, projectMapper, clock);
  }

  @Test
  void getOrCreateEnvId_requiresEnvName() {
    RcDictionaryService service = service();
    assertThrows(BusinessException.class, () -> service.getOrCreateEnvId(null));
    assertThrows(BusinessException.class, () -> service.getOrCreateEnvId(""));
  }

  @Test
  void getOrCreateEnvId_returnsExisting() {
    RcDictionaryService service = service();
    RcEnv env = new RcEnv();
    env.setId(12L);
    when(envMapper.selectOne(any())).thenReturn(env);

    assertEquals(12L, service.getOrCreateEnvId("dev"));
  }

  @Test
  void getOrCreateEnvId_insertsWhenMissing() {
    RcDictionaryService service = service();
    when(envMapper.selectOne(any())).thenReturn(null);
    doAnswer(inv -> {
      RcEnv arg = inv.getArgument(0);
      arg.setId(99L);
      return 1;
    }).when(envMapper).insert(any(RcEnv.class));

    assertEquals(99L, service.getOrCreateEnvId("dev"));
  }

  @Test
  void getOrCreateProjectId_requiresProjectName() {
    RcDictionaryService service = service();
    assertThrows(BusinessException.class, () -> service.getOrCreateProjectId(null));
    assertThrows(BusinessException.class, () -> service.getOrCreateProjectId(""));
  }

  @Test
  void getOrCreateProjectId_returnsExisting() {
    RcDictionaryService service = service();
    RcProject project = new RcProject();
    project.setId(7L);
    when(projectMapper.selectOne(any())).thenReturn(project);
    assertEquals(7L, service.getOrCreateProjectId("order-service"));
  }

  @Test
  void getOrCreateProjectId_insertsWhenMissing() {
    RcDictionaryService service = service();
    when(projectMapper.selectOne(any())).thenReturn(null);
    doAnswer(inv -> {
      RcProject arg = inv.getArgument(0);
      arg.setId(100L);
      return 1;
    }).when(projectMapper).insert(any(RcProject.class));

    assertEquals(100L, service.getOrCreateProjectId("order-service"));
  }
}
