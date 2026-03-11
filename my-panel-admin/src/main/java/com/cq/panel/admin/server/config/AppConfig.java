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

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    /** 获取地址开关 */
    private boolean addressEnabled;

    /** 验证码类型 */
    private String captchaType;

    public boolean isAddressEnabled() {
        return addressEnabled;
    }

    public void setAddressEnabled(boolean addressEnabled) {
        this.addressEnabled = addressEnabled;
    }

    public String getCaptchaType() {
        return captchaType;
    }

    public void setCaptchaType(String captchaType) {
        this.captchaType = captchaType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

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

    public String getImportPath() {
        return importPath;
    }

    public void setImportPath(String importPath) {
        this.importPath = importPath;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }

    public String getDownloadPath() {
        return downloadPath;
    }

    public void setDownloadPath(String downloadPath) {
        this.downloadPath = downloadPath;
    }

    public String getUploadPath() {
        return uploadPath;
    }

    public void setUploadPath(String uploadPath) {
        this.uploadPath = uploadPath;
    }
}
