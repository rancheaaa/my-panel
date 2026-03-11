package com.cq.panel.admin.server.web.controller.rc;

import com.cq.panel.admin.server.repository.domain.RcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RcConfigExportTest {

    @Autowired
    private RcConfigController rcConfigController;

    @Test
    public void testConvertToNestedMap() throws Exception {
        // 使用反射访问私有方法 convertToNestedMap
        Method method = RcConfigController.class.getDeclaredMethod("convertToNestedMap", List.class);
        method.setAccessible(true);

        List<RcConfig> configs = new ArrayList<>();
        
        // 1. 测试常规嵌套
        configs.add(createConfig("server.port", "8080", "端口"));
        configs.add(createConfig("server.servlet.context-path", "/", "路径"));
        
        // 2. 测试日志级别 (包含包名)
        configs.add(createConfig("logging.level.com.cq.panel.admin.server", "debug", "日志1"));
        configs.add(createConfig("logging.level.org.springframework", "warn", "日志2"));
        
        // 3. 测试带大写字母的类名
        configs.add(createConfig("mybatis.type-aliases-package", "com.cq.panel.admin.server.domain", "别名"));
        configs.add(createConfig("spring.datasource.druid.InitialSize", "5", "初始连接"));

        Map<String, Object> result = (Map<String, Object>) method.invoke(rcConfigController, configs);

        System.out.println("Result Map: " + result);

        // 验证 server 嵌套
        assertTrue(result.containsKey("server"));
        Map<String, Object> server = (Map<String, Object>) result.get("server");
        assertNotNull(server.get("port"));
        
        // 验证 logging.level 嵌套
        assertTrue(result.containsKey("logging"));
        Map<String, Object> logging = (Map<String, Object>) result.get("logging");
        Map<String, Object> level = (Map<String, Object>) logging.get("level");
        
        // 验证包名是否被正确保留 (不再被进一步拆分)
        assertTrue(level.containsKey("com.cq.panel.admin.server"));
        assertTrue(level.containsKey("org.springframework"));
        
        // 验证类名/大写字母处理
        assertTrue(result.containsKey("mybatis"));
        Map<String, Object> mybatis = (Map<String, Object>) result.get("mybatis");
        // type-aliases-package 应该在 mybatis 下作为完整 key
        assertTrue(mybatis.containsKey("type-aliases-package"));
        
        assertTrue(result.containsKey("spring"));
        Map<String, Object> spring = (Map<String, Object>) result.get("spring");
        Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
        Map<String, Object> druid = (Map<String, Object>) datasource.get("druid");
        // InitialSize 包含大写，应作为完整 key
        assertTrue(druid.containsKey("InitialSize"));
    }

    private RcConfig createConfig(String key, String value, String desc) {
        RcConfig config = new RcConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setConfigDesc(desc);
        return config;
    }
}
