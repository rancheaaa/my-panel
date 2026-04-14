package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 健康检查器工厂测试
 */
@DisplayName("健康检查器工厂测试")
class HealthCheckerFactoryTest {
    
    @Test
    @DisplayName("创建默认健康检查器")
    void testCreateDefault() {
        HealthChecker checker = HealthCheckerFactory.createDefault();
        
        assertNotNull(checker);
        assertInstanceOf(TcpHealthChecker.class, checker);
        assertEquals("tcpHealthChecker", checker.getName());
    }
    
    @Test
    @DisplayName("根据类型创建TCP健康检查器")
    void testCreateTcpHealthChecker() {
        HealthChecker checker = HealthCheckerFactory.create(
                HealthCheckerFactory.HealthCheckerType.TCP, 
                5000, 
                "/health"
        );
        
        assertNotNull(checker);
        assertInstanceOf(TcpHealthChecker.class, checker);
        assertEquals("tcpHealthChecker", checker.getName());
    }
    
    @Test
    @DisplayName("根据类型创建HTTP健康检查器")
    void testCreateHttpHealthChecker() {
        HealthChecker checker = HealthCheckerFactory.create(
                HealthCheckerFactory.HealthCheckerType.HTTP, 
                3000, 
                "/health/status"
        );
        
        assertNotNull(checker);
        assertInstanceOf(HttpHealthChecker.class, checker);
        assertEquals("httpHealthChecker", checker.getName());
    }
    
    @Test
    @DisplayName("根据配置创建健康检查器")
    void testCreateWithConfig() {
        LoadBalancerConfig config = LoadBalancerConfig.builder()
                .healthCheckTimeout(4000)
                .healthCheckPath("/api/health")
                .build();
        
        // 测试TCP类型
        HealthChecker tcpChecker = HealthCheckerFactory.create(config, HealthCheckerFactory.HealthCheckerType.TCP);
        assertNotNull(tcpChecker);
        assertInstanceOf(TcpHealthChecker.class, tcpChecker);
        
        // 测试HTTP类型
        HealthChecker httpChecker = HealthCheckerFactory.create(config, HealthCheckerFactory.HealthCheckerType.HTTP);
        assertNotNull(httpChecker);
        assertInstanceOf(HttpHealthChecker.class, httpChecker);
        
        // 测试默认类型
        HealthChecker defaultChecker = HealthCheckerFactory.create((LoadBalancerConfig) config);
        assertNotNull(defaultChecker);
        assertInstanceOf(TcpHealthChecker.class, defaultChecker);
    }
    
    @Test
    @DisplayName("使用空配置创建健康检查器")
    void testCreateWithNullConfig() {
        HealthChecker checker = HealthCheckerFactory.create(null, HealthCheckerFactory.HealthCheckerType.TCP);
        
        assertNotNull(checker);
        assertInstanceOf(TcpHealthChecker.class, checker);
        assertEquals("tcpHealthChecker", checker.getName());
    }
    
    @Test
    @DisplayName("测试健康检查器类型枚举")
    void testHealthCheckerTypeEnum() {
        // 验证枚举值
        assertEquals(2, HealthCheckerFactory.HealthCheckerType.values().length);
        assertNotNull(HealthCheckerFactory.HealthCheckerType.TCP);
        assertNotNull(HealthCheckerFactory.HealthCheckerType.HTTP);
        
        // 验证枚举名称
        assertEquals("TCP", HealthCheckerFactory.HealthCheckerType.TCP.name());
        assertEquals("HTTP", HealthCheckerFactory.HealthCheckerType.HTTP.name());
    }
    
    @Test
    @DisplayName("测试未知类型创建默认检查器")
    void testCreateWithUnknownType() {
        // 测试传入null类型时返回默认检查器
        HealthChecker checker = HealthCheckerFactory.create(null, 5000, "/health");
        
        assertNotNull(checker);
        assertInstanceOf(TcpHealthChecker.class, checker);
    }
}