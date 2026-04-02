package com.cq.panel.admin.server.repository.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 缓存信息
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SysCache extends BaseEntity
{
    /** 缓存名称 */
    private String cacheName = "";

    /** 缓存键名 */
    private String cacheKey = "";

    /** 缓存内容 */
    private String cacheValue = "";

    /** 备注 */
    private String remark = "";

    /** 剩余时间 */
    private String ttl = "";

    /**
     * 带参数构造器
     * @param cacheKey 缓存键名
     * @param cacheName 缓存名称
     */
    public SysCache(String cacheKey, String cacheName) {
        this.cacheKey = cacheKey;
        this.cacheName = cacheName;
    }
}