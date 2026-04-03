package com.cq.panel.admin.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 读取项目相关配置
 * 
 * @author cq
 */
@Component
@ConfigurationProperties(prefix = "app")
@Data
public class AppConfig
{
    /** 项目名称 */
    private String name;

    /** 版本 */
    private String version;

    /** 版权年份 */
    private String copyrightYear;

    /** 上传路径 */
    private String profile;

    /** 获取地址开关 */
    private boolean addressEnabled;

    /** 验证码类型 */
    private String captchaType;

    /**
     * 导入上传路径
     */
    private String importPath;

    /**
     * 获取头像上传路径
     */
    private String avatarPath;

    /**
     * 获取下载路径
     */
    private String downloadPath;

    /**
     * 获取上传路径
     */
    private String uploadPath;
}
