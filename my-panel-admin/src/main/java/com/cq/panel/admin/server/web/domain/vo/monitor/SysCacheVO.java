package com.cq.panel.admin.server.web.domain.vo.monitor;

import com.cq.panel.admin.server.common.utils.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "缓存信息视图对象")
public class SysCacheVO {

    @Schema(description = "缓存名称", example = "login_tokens")
    private String cacheName = "";

    @Schema(description = "缓存键名", example = "login_tokens:uuid")
    private String cacheKey = "";

    @Schema(description = "缓存内容", example = "{\"user\": \"admin\"}")
    private String cacheValue = "";

    @Schema(description = "备注", example = "用户信息")
    private String remark = "";
    
    @Schema(description = "过期时间", example = "3600")
    private String ttl = "";

    public SysCacheVO(String cacheName, String remark)
    {
        this.cacheName = cacheName;
        this.remark = remark;
    }

    public SysCacheVO(String cacheName, String cacheKey, String cacheValue)
    {
        this.cacheName = StringUtils.replace(cacheName, ":", "");
        this.cacheKey = StringUtils.replace(cacheKey, cacheName, "");
        this.cacheValue = cacheValue;
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public String getCacheValue() {
        return cacheValue;
    }

    public void setCacheValue(String cacheValue) {
        this.cacheValue = cacheValue;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getTtl() {
        return ttl;
    }

    public void setTtl(String ttl) {
        this.ttl = ttl;
    }
}
