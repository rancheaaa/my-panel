package com.cq.panel.admin.server.repository.service;

import java.util.List;
import java.util.Map;
import com.cq.panel.admin.server.repository.domain.RcConfig;

/**
 * 配置中心Service接口
 * 
 * @author cq
 */
public interface IRcConfigService 
{
    /**
     * 查询配置中心
     * 
     * @param id 配置中心主键
     * @return 配置中心
     */
    public RcConfig selectRcConfigById(Long id);

    /**
     * 查询配置中心列表
     * 
     * @param rcConfig 配置中心
     * @return 配置中心集合
     */
    public List<RcConfig> selectRcConfigList(RcConfig rcConfig);

    /**
     * 新增配置中心
     * 
     * @param rcConfig 配置中心
     * @return 结果
     */
    public int insertRcConfig(RcConfig rcConfig);

    /**
     * 修改配置中心
     * 
     * @param rcConfig 配置中心
     * @return 结果
     */
    public int updateRcConfig(RcConfig rcConfig);

    /**
     * 批量删除配置中心
     * 
     * @param ids 需要删除的配置中心主键集合
     * @return 结果
     */
    public int deleteRcConfigByIds(Long[] ids);

    /**
     * 删除配置中心信息
     * 
     * @param id 配置中心主键
     * @return 结果
     */
    public int deleteRcConfigById(Long id);

    /**
     * 校验配置键是否唯一
     * 
     * @param rcConfig 配置信息
     * @return 结果
     */
    public boolean checkConfigKeyUnique(RcConfig rcConfig);

    /**
     * 批量导入配置
     * 
     * @param envId 环境ID
     * @param projectId 项目ID
     * @param content 配置内容
     * @param format 格式 (properties/yaml)
     * @param operName 操作人
     */
    public void importConfig(Long envId, Long projectId, String content, String format, String operName);

    /**
     * 转换配置列表为嵌套Map结构
     * 
     * @param list 配置列表
     * @return 嵌套Map
     */
    public Map<String, Object> convertToNestedMap(List<RcConfig> list);

    /**
     * 导出配置
     * 
     * @param list 配置列表
     * @param format 导出格式
     * @param envId 环境ID
     * @param projectId 项目ID
     * @return 导出结果（包含内容、Content-Type、文件名、扩展名）
     */
    public ExportResult exportConfigs(List<RcConfig> list, String format, Long envId, Long projectId);

    /**
     * 导出结果包装类
     */
    public static class ExportResult {
        private String content;
        private String contentType;
        private String fileName;
        private String extension;
        private boolean isExcel;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getExtension() { return extension; }
        public void setExtension(String extension) { this.extension = extension; }
        public boolean isExcel() { return isExcel; }
        public void setExcel(boolean excel) { isExcel = excel; }
    }
}
