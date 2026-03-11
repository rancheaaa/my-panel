package com.cq.panel.admin.server.web.service.cache.memory;

import com.cq.panel.admin.server.web.service.cache.CacheService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.PatternMatchUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine Cache implementation replacing old MemoryCache
 *
 * @author cq
 **/
@SuppressWarnings(value = { "unchecked", "rawtypes" })
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "standalone")
public class MemoryCache implements CacheService
{
    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .expireAfter(new Expiry<String, Object>() {
                @Override
                public long expireAfterCreate(String key, Object value, long currentTime) {
                    if (value instanceof CacheObject) {
                        long expireAt = ((CacheObject) value).expireAt;
                        return expireAt == -1 ? Long.MAX_VALUE : TimeUnit.MILLISECONDS.toNanos(expireAt - System.currentTimeMillis());
                    }
                    return Long.MAX_VALUE;
                }

                @Override
                public long expireAfterUpdate(String key, Object value, long currentTime, long currentDuration) {
                    if (value instanceof CacheObject) {
                        long expireAt = ((CacheObject) value).expireAt;
                        return expireAt == -1 ? Long.MAX_VALUE : TimeUnit.MILLISECONDS.toNanos(expireAt - System.currentTimeMillis());
                    }
                    return currentDuration;
                }

                @Override
                public long expireAfterRead(String key, Object value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    /**
     * Wrapper object to store value and expiration info
     */
    private static class CacheObject {
        Object value;
        long expireAt; // timestamp in millis, -1 for no expiration

        CacheObject(Object value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }

    @Override
    public <T> void set(String key, T value) {
        cache.put(key, new CacheObject(value, -1));
    }

    @Override
    public <T> void set(String key, T value, Integer timeout, TimeUnit timeUnit) {
        long expireAt = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        cache.put(key, new CacheObject(value, expireAt));
    }

    @Override
    public boolean expire(String key, long timeout) {
        return expire(key, timeout, TimeUnit.SECONDS);
    }

    @Override
    public boolean expire(String key, long timeout, TimeUnit unit) {
        CacheObject entry = (CacheObject) cache.getIfPresent(key);
        if (entry != null) {
            entry.expireAt = System.currentTimeMillis() + unit.toMillis(timeout);
            // Re-put to trigger expiry update
            cache.put(key, entry);
            return true;
        }
        return false;
    }

    @Override
    public long getExpire(String key) {
        CacheObject entry = (CacheObject) cache.getIfPresent(key);
        if (entry == null) {
            return -2; // Not found
        }
        if (entry.expireAt == -1) {
            return -1; // No expiration
        }
        return TimeUnit.MILLISECONDS.toSeconds(entry.expireAt - System.currentTimeMillis());
    }

    @Override
    public Boolean hasKey(String key) {
        return cache.getIfPresent(key) != null;
    }

    @Override
    public <T> T get(String key) {
        CacheObject entry = (CacheObject) cache.getIfPresent(key);
        return entry != null ? (T) entry.value : null;
    }

    @Override
    public boolean delete(String key) {
        boolean exists = cache.getIfPresent(key) != null;
        cache.invalidate(key);
        return exists;
    }

    @Override
    public boolean delete(Collection collection) {
        if (collection == null || collection.isEmpty()) {
            return false;
        }
        cache.invalidateAll(collection);
        return true;
    }

    @Override
    public <T> long setList(String key, List<T> dataList) {
        set(key, dataList);
        return dataList != null ? dataList.size() : 0;
    }

    @Override
    public <T> List<T> getList(String key) {
        return get(key);
    }

    @Override
    public <T> Set<T> getSet(String key) {
        return get(key);
    }
    
    @Override
    public <T> void setMap(String key, Map<String, T> dataMap) {
        set(key, dataMap);
    }

    @Override
    public <T> Map<String, T> getMap(String key) {
        return get(key);
    }

    @Override
    public <T> void setMapValue(String key, String hKey, T value) {
        Map<String, T> map = get(key);
        if (map == null) {
            map = new HashMap<>();
            set(key, map);
        }
        map.put(hKey, value);
    }

    @Override
    public <T> T getMapValue(String key, String hKey) {
        Map<String, T> map = get(key);
        return map != null ? map.get(hKey) : null;
    }

    @Override
    public <T> List<T> getMultiMapValue(String key, Collection<Object> hKeys) {
        Map<String, T> map = get(key);
        List<T> list = new ArrayList<>();
        if (map != null && hKeys != null) {
            for (Object hKey : hKeys) {
                list.add(map.get(hKey));
            }
        }
        return list;
    }

    @Override
    public boolean deleteMapValue(String key, String hKey) {
        Map<String, Object> map = get(key);
        if (map != null) {
            return map.remove(hKey) != null;
        }
        return false;
    }

    @Override
    public Collection<String> keys(String pattern) {
        List<String> keys = new ArrayList<>();
        for (String key : cache.asMap().keySet()) {
            if (PatternMatchUtils.simpleMatch(pattern, key)) {
                keys.add(key);
            }
        }
        return keys;
    }
}
