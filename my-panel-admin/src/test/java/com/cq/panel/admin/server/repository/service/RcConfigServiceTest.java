package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.RcConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RcConfigServiceTest {
    private static final Logger log = LoggerFactory.getLogger(RcConfigServiceTest.class);

    @Autowired
    private IRcConfigService rcConfigService;

    @SuppressWarnings("unchecked")
    @Test
    public void testConvertToNestedMap() {
        List<RcConfig> configs = new ArrayList<>();
        
        // 1. 测试常规嵌套
        configs.add(createConfig("server.port", "8080", "端口"));
        configs.add(createConfig("server.servlet.context-path", "/", "路径"));
        
        // 2. 测试日志级别 (包名识别)
        configs.add(createConfig("logging.level.com.cq.panel.admin.server", "debug", "日志1"));
        configs.add(createConfig("logging.level.org.springframework", "warn", "日志2"));
        
        // 3. 测试大写字母 (类名识别)
        configs.add(createConfig("spring.datasource.druid.InitialSize", "5", "初始连接"));
        
        // 4. 测试特定前缀 (Mybatis 包名)
        configs.add(createConfig("mybatis.type-aliases-package", "com.cq.domain", "别名"));

        Map<String, Object> result = rcConfigService.convertToNestedMap(configs);

        // 验证结果
        assertNotNull(result);
        
        // 验证 server 嵌套
        assertTrue(result.containsKey("server"));
        Object serverObj = result.get("server");
        assertTrue(serverObj instanceof Map);
        Map<String, Object> server = (Map<String, Object>) serverObj;
        assertNotNull(server.get("port"));
        
        // 验证 logging.level (包名不拆分)
        Map<String, Object> logging = (Map<String, Object>) result.get("logging");
        Map<String, Object> level = (Map<String, Object>) logging.get("level");
        assertTrue(level.containsKey("com.cq.panel.admin.server"));
        assertTrue(level.containsKey("org.springframework"));
        
        // 验证 InitialSize (大写不拆分)
        Map<String, Object> spring = (Map<String, Object>) result.get("spring");
        Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
        Map<String, Object> druid = (Map<String, Object>) datasource.get("druid");
        assertTrue(druid.containsKey("InitialSize"));
        
        // 验证 mybatis (特定前缀不拆分)
        Map<String, Object> mybatis = (Map<String, Object>) result.get("mybatis");
        assertTrue(mybatis.containsKey("type-aliases-package"));
    }

    @Test
    public void testExportConfigs() {
        List<RcConfig> configs = new ArrayList<>();
        configs.add(createConfig("server.port", "8080", "端口"));
        configs.add(createConfig("logging.level.com.cq", "debug", "日志"));

        // 测试 YAML 导出
        IRcConfigService.ExportResult yamlResult = rcConfigService.exportConfigs(configs, "yaml", null, null);
        assertNotNull(yamlResult);
        assertEquals("application/x-yaml", yamlResult.getContentType());
        assertTrue(yamlResult.getContent().contains("server:"));
        assertTrue(yamlResult.getContent().contains("port: 8080"));

        // 测试 JSON 导出
        IRcConfigService.ExportResult jsonResult = rcConfigService.exportConfigs(configs, "json", null, null);
        assertEquals("application/json", jsonResult.getContentType());
        assertTrue(jsonResult.getContent().contains("\"server\""));

        // 测试 Excel 导出标识
        IRcConfigService.ExportResult excelResult = rcConfigService.exportConfigs(configs, "excel", null, null);
        assertTrue(excelResult.isExcel());
    }

    @Test
    public void testSelectRcConfigList() {
        RcConfig rcConfig = new RcConfig();
        List<RcConfig> list = rcConfigService.selectRcConfigList(rcConfig);
        log.info("Total configs found: {}", list.size());
        for (RcConfig config : list) {
            log.info("Config: ID={}, EnvId={}, ProjectId={}, Key={}", 
                config.getId(), config.getEnvId(), config.getProjectId(), config.getConfigKey());
        }
    }

    private RcConfig createConfig(String key, String value, String desc) {
        RcConfig config = new RcConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setConfigDesc(desc);
        return config;
    }
}
