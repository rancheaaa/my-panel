package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import com.cq.panel.admin.server.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cq.panel.admin.server.repository.mapper.RcConfigMapper;
import com.cq.panel.admin.server.repository.domain.RcConfig;
import com.cq.panel.admin.server.repository.service.IRcConfigService;
import com.cq.panel.admin.server.common.constant.UserConstants;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.repository.domain.RcEnv;
import com.cq.panel.admin.server.repository.domain.RcProject;
import com.cq.panel.admin.server.repository.service.IRcEnvService;
import com.cq.panel.admin.server.repository.service.IRcProjectService;
import com.cq.panel.admin.server.common.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.PropertiesConfigurationLayout;
import java.io.StringReader;
import java.util.*;

/**
 * 配置中心Service业务层处理
 * 
 * @author cq
 */
@Service
public class RcConfigServiceImpl implements IRcConfigService 
{
    private static final Logger log = LoggerFactory.getLogger(RcConfigServiceImpl.class);

    @Autowired
    private RcConfigMapper rcConfigMapper;

    @Autowired
    private IRcEnvService rcEnvService;

    @Autowired
    private IRcProjectService rcProjectService;

    /**
     * 配置值及描述包装类
     */
    public static class ConfigValueWithDesc {
        private final String value;
        private final String desc;

        public ConfigValueWithDesc(String value, String desc) {
            this.value = value;
            this.desc = desc;
        }

        public String getValue() { return value; }
        public String getDesc() { return desc; }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * 判断是否为包名或类名部分
     */
    private boolean isPackageOrClassName(String part) {
        if (StringUtils.isEmpty(part)) return false;
        // 包含大写字母（类名特征）
        if (!part.equals(part.toLowerCase())) return true;
        // 包含数字（通常不是配置层级）
        return part.matches(".*\\d.*");
    }

    /**
     * 判断拆分索引
     */
    private int getSplitIndex(String key) {
        String[] parts = key.split("\\.");
        if (parts.length <= 1) return 0;

        // 特殊处理：Spring Boot 常见的包名容器 Key
        if (key.startsWith("logging.level.")) return 2;
        if (key.startsWith("mybatis.type-aliases-package")) return 1;

        for (int i = 0; i < parts.length - 1; i++) {
            if (isPackageOrClassName(parts[i])) {
                return i;
            }
        }
        return parts.length - 1;
    }

    @Override
    public Map<String, Object> convertToNestedMap(List<RcConfig> list) {
        log.info("Converting {} configs to nested map", list.size());
        Map<String, Object> result = new LinkedHashMap<>();
        for (RcConfig config : list) {
            String key = config.getConfigKey();
            if (StringUtils.isEmpty(key)) continue;
            
            ConfigValueWithDesc value = new ConfigValueWithDesc(config.getConfigValue(), config.getConfigDesc());
            
            String[] parts = key.split("\\.");
            Map<String, Object> current = result;
            
            int splitIndex = getSplitIndex(key);

            // 处理嵌套部分
            for (int i = 0; i < splitIndex; i++) {
                Object next = current.computeIfAbsent(parts[i], k -> new LinkedHashMap<String, Object>());
                if (next instanceof Map) {
                    current = (Map<String, Object>) next;
                } else {
                    Map<String, Object> newNode = new LinkedHashMap<>();
                    current.put(parts[i], newNode);
                    current = newNode;
                }
            }

            // 处理剩余部分
            StringBuilder finalKey = new StringBuilder();
            for (int i = splitIndex; i < parts.length; i++) {
                if (!finalKey.isEmpty()) finalKey.append(".");
                finalKey.append(parts[i]);
            }
            current.put(finalKey.toString(), value);
        }
        return result;
    }

    @Override
    public ExportResult exportConfigs(List<RcConfig> list, String format, Long envId, Long projectId) {
        ExportResult result = new ExportResult();
        
        // 1. 获取文件名相关信息
        String envName = "unknown_env";
        String projectName = "unknown_project";
        if (envId != null) {
            RcEnv env = rcEnvService.selectRcEnvById(envId);
            if (env != null) envName = env.getEnvName();
        }
        if (projectId != null) {
            RcProject project = rcProjectService.selectRcProjectById(projectId);
            if (project != null) projectName = project.getProjectName();
        }
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String fileName = String.format("%s_%s_%s", envName, projectName, uuid);
        result.setFileName(fileName);

        if (format == null || "excel".equalsIgnoreCase(format)) {
            result.setExcel(true);
            return result;
        }

        String content = "";
        String contentType = "text/plain";
        String extension = format.toLowerCase();

        if ("json".equals(extension) || "yml".equals(extension) || "yaml".equals(extension)) {
            Map<String, Object> nestedMap = convertToNestedMap(list);
            log.info("Nested map keys: {}", nestedMap.keySet());
            if ("json".equals(extension)) {
                Map<String, Object> jsonMap = stripDesc(nestedMap);
                content = JsonUtils.toPrettyJSONString(jsonMap);
                contentType = "application/json";
            } else {
                StringBuilder yamlSb = new StringBuilder();
                buildYaml(yamlSb, nestedMap, 0);
                content = yamlSb.toString();
                contentType = "application/x-yaml";
            }
        } else if ("properties".equals(extension)) {
            StringBuilder sb = new StringBuilder();
            list.forEach(c -> {
                if (StringUtils.isNotEmpty(c.getConfigDesc())) {
                    sb.append("# ").append(c.getConfigDesc()).append("\n");
                }
                sb.append(c.getConfigKey()).append("=").append(c.getConfigValue()).append("\n");
            });
            content = sb.toString();
        } else {
            StringBuilder txtSb = new StringBuilder();
            list.forEach(c -> {
                if (StringUtils.isNotEmpty(c.getConfigDesc())) {
                    txtSb.append("# ").append(c.getConfigDesc()).append("\n");
                }
                txtSb.append(c.getConfigKey()).append(": ").append(c.getConfigValue()).append("\n");
            });
            content = txtSb.toString();
            extension = "txt";
        }

        result.setContent(content);
        result.setContentType(contentType);
        result.setExtension(extension);
        result.setExcel(false);
        return result;
    }

    private void buildYaml(StringBuilder sb, Map<String, Object> map, int indent) {
        String space = "  ".repeat(indent);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                sb.append(space).append(key).append(":\n");
                buildYaml(sb, (Map<String, Object>) value, indent + 1);
            } else if (value instanceof ConfigValueWithDesc cv) {
                if (StringUtils.isNotEmpty(cv.getDesc())) {
                    sb.append(space).append("# ").append(cv.getDesc()).append("\n");
                }
                sb.append(space).append(key).append(": ").append(cv.getValue()).append("\n");
            }
        }
    }

    private Map<String, Object> stripDesc(Map<String, Object> nestedMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : nestedMap.entrySet()) {
            if (entry.getValue() instanceof Map) {
                result.put(entry.getKey(), stripDesc((Map<String, Object>) entry.getValue()));
            } else if (entry.getValue() instanceof ConfigValueWithDesc) {
                result.put(entry.getKey(), ((ConfigValueWithDesc) entry.getValue()).getValue());
            }
        }
        return result;
    }

    /**
     * 查询配置中心
     * 
     * @param id 配置中心主键
     * @return 配置中心
     */
    @Override
    public RcConfig selectRcConfigById(Long id)
    {
        return rcConfigMapper.selectRcConfigById(id);
    }

    /**
     * 查询配置中心列表
     * 
     * @param rcConfig 配置中心
     * @return 配置中心
     */
    @Override
    public List<RcConfig> selectRcConfigList(RcConfig rcConfig)
    {
        return rcConfigMapper.selectRcConfigList(rcConfig);
    }

    /**
     * 新增配置中心
     * 
     * @param rcConfig 配置中心
     * @return 结果
     */
    @Override
    public int insertRcConfig(RcConfig rcConfig)
    {
        rcConfig.setCreateTime(DateUtils.getNowDate());
        return rcConfigMapper.insertRcConfig(rcConfig);
    }

    /**
     * 修改配置中心
     * 
     * @param rcConfig 配置中心
     * @return 结果
     */
    @Override
    public int updateRcConfig(RcConfig rcConfig)
    {
        rcConfig.setUpdateTime(DateUtils.getNowDate());
        return rcConfigMapper.updateRcConfig(rcConfig);
    }

    /**
     * 批量删除配置中心
     * 
     * @param ids 需要删除的配置中心主键
     * @return 结果
     */
    @Override
    public int deleteRcConfigByIds(Long[] ids)
    {
        return rcConfigMapper.deleteRcConfigByIds(ids);
    }

    /**
     * 删除配置中心信息
     * 
     * @param id 配置中心主键
     * @return 结果
     */
    @Override
    public int deleteRcConfigById(Long id)
    {
        return rcConfigMapper.deleteRcConfigById(id);
    }

    /**
     * 校验配置键是否唯一
     * 
     * @param rcConfig 配置信息
     * @return 结果
     */
    @Override
    public boolean checkConfigKeyUnique(RcConfig rcConfig)
    {
        Long id = StringUtils.isNull(rcConfig.getId()) ? -1L : rcConfig.getId();
        RcConfig info = rcConfigMapper.checkConfigKeyUnique(rcConfig.getEnvId(), rcConfig.getProjectId(), rcConfig.getConfigKey());
        if (StringUtils.isNotNull(info) && info.getId().longValue() != id.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 批量导入配置
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importConfig(Long envId, Long projectId, String content, String format, String operName)
    {
        log.info("Starting batch import: envId={}, projectId={}, format={}, operator={}", envId, projectId, format, operName);
        if (StringUtils.isEmpty(content))
        {
            return;
        }

        try {
            if ("yaml".equalsIgnoreCase(format) || "yml".equalsIgnoreCase(format))
            {
                Yaml yaml = new Yaml();
                Map<String, Object> map = yaml.load(content);
                parseYamlMap("", map, envId, projectId, operName);
            }
            else
            {
                // 使用commons-configuration2解析properties文件，支持读取注释
                PropertiesConfiguration config = new PropertiesConfiguration();
                config.read(new StringReader(content));
                
                // 获取布局信息，包含注释
                PropertiesConfigurationLayout layout = config.getLayout();

                for (Iterator<String> it = config.getKeys(); it.hasNext(); ) {
                    String key = it.next();
                    String value = config.getString(key);
                    String comment = layout.getComment(key);

                    // 保存配置项和注释信息
                    saveOrUpdateConfigWithComment(envId, projectId, key, value, comment, operName);
                }
            }
            log.info("Batch import completed successfully for envId={}, projectId={}", envId, projectId);
        } catch (Exception e) {
            log.error("Batch import failed for envId={}, projectId={}. Error: {}", envId, projectId, e.getMessage());
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("批量导入失败，已回滚: " + e.getMessage());
        }
    }

    private void parseYamlMap(String prefix, Map<String, Object> map, Long envId, Long projectId, String operName)
    {
        if (map == null) return;
        for (Map.Entry<String, Object> entry : map.entrySet())
        {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map)
            {
                parseYamlMap(key, (Map<String, Object>) value, envId, projectId, operName);
            }
            else
            {
                saveOrUpdateConfig(envId, projectId, key, value == null ? "" : value.toString(), operName);
            }
        }
    }

    /**
     * 保存或更新配置项，包含注释信息
     */
    private void saveOrUpdateConfigWithComment(Long envId, Long projectId, String key, String value, String comment, String operName)
    {
        RcConfig rcConfig = new RcConfig();
        rcConfig.setEnvId(envId);
        rcConfig.setProjectId(projectId);
        rcConfig.setConfigKey(key);
        rcConfig.setConfigValue(value);
        
        // 设置注释信息，如果注释为空则使用空字符串
        if (StringUtils.isNotEmpty(comment)) {
            // 清理注释中的注释符号和换行符
            comment = comment.replace("#", "").replace("!", "");
            // 限制注释长度不超过200字符
            if (comment.length() > 200) {
                comment = comment.substring(0, 200);
            }
        }
        rcConfig.setConfigDesc(StringUtils.isEmpty(comment) ? "" : comment);

        // 1: 批量导入
        rcConfig.setSource("1");

        RcConfig exist = rcConfigMapper.checkConfigKeyUnique(envId, projectId, key);
        if (exist != null)
        {
            rcConfig.setId(exist.getId());
            rcConfig.setUpdateBy(operName);
            updateRcConfig(rcConfig);
        }
        else
        {
            rcConfig.setCreateBy(operName);
            insertRcConfig(rcConfig);
        }
    }

    private void saveOrUpdateConfig(Long envId, Long projectId, String key, String value, String operName)
    {
        RcConfig rcConfig = new RcConfig();
        rcConfig.setEnvId(envId);
        rcConfig.setProjectId(projectId);
        rcConfig.setConfigKey(key);
        rcConfig.setConfigValue(value);
        rcConfig.setSource("1"); // 1: 批量导入
        
        RcConfig exist = rcConfigMapper.checkConfigKeyUnique(envId, projectId, key);
        if (exist != null)
        {
            rcConfig.setId(exist.getId());
            rcConfig.setUpdateBy(operName);
            updateRcConfig(rcConfig);
        }
        else
        {
            rcConfig.setCreateBy(operName);
            insertRcConfig(rcConfig);
        }
    }
}