package com.cq.panel.admin.server.web.service.cache;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 缓存接口抽象
 */
public interface CacheService {
    
    <T> void set(final String key, final T value);
    
    <T> void set(final String key, final T value, final Integer timeout, final TimeUnit timeUnit);
    
    boolean expire(final String key, final long timeout);
    
    boolean expire(final String key, final long timeout, final TimeUnit unit);
    
    long getExpire(final String key);
    
    Boolean hasKey(String key);
    
    <T> T get(final String key);
    
    boolean delete(final String key);
    
    boolean delete(final Collection collection);
    
    <T> long setList(final String key, final List<T> dataList);
    
    <T> List<T> getList(final String key);
    
    <T> Set<T> getSet(final String key);
    
    <T> void setMap(final String key, final Map<String, T> dataMap);
    
    <T> Map<String, T> getMap(final String key);
    
    <T> void setMapValue(final String key, final String hKey, final T value);
    
    <T> T getMapValue(final String key, final String hKey);
    
    <T> List<T> getMultiMapValue(final String key, final Collection<Object> hKeys);
    
    boolean deleteMapValue(final String key, final String hKey);
    
    Collection<String> keys(final String pattern);
}
