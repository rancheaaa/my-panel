package com.cq.panel.admin.server.web.controller.monitor;

import com.cq.panel.admin.server.common.constant.CacheConstants;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.monitor.CacheInfoVO;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysCacheVO;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.repository.domain.SysCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.cq.panel.admin.server.web.service.cache.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 缓存监控
 * 
 * @author cq
 */
@Tag(name = "缓存监控", description = "Redis缓存监控相关接口")
@RestController
@RequestMapping("/monitor/cache")
public class CacheController
{
    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private CacheService cacheService;

    @org.springframework.beans.factory.annotation.Value("${app.mode:cluster}")
    private String appMode;

    private final static List<SysCache> caches = new ArrayList<SysCache>();
    {
        caches.add(new SysCache(CacheConstants.LOGIN_TOKEN_KEY, "用户信息"));
        caches.add(new SysCache(CacheConstants.SYS_CONFIG_KEY, "配置信息"));
        caches.add(new SysCache(CacheConstants.SYS_DICT_KEY, "数据字典"));
        caches.add(new SysCache(CacheConstants.CAPTCHA_CODE_KEY, "验证码"));
        caches.add(new SysCache(CacheConstants.REPEAT_SUBMIT_KEY, "防重提交"));
        caches.add(new SysCache(CacheConstants.RATE_LIMIT_KEY, "限流处理"));
        caches.add(new SysCache(CacheConstants.PWD_ERR_CNT_KEY, "密码错误次数"));
    }

    @SuppressWarnings("deprecation")
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @Operation(summary = "获取缓存监控信息", description = "获取Redis内存、命令统计等监控信息")
    @GetMapping()
    public Result<CacheInfoVO> getInfo() throws Exception
    {
        CacheInfoVO result = new CacheInfoVO();
        
        if ("cluster".equalsIgnoreCase(appMode) && redisTemplate != null) {
            Properties info = (Properties) redisTemplate.execute((RedisCallback<Object>) connection -> connection.info());
            Properties commandStats = (Properties) redisTemplate.execute((RedisCallback<Object>) connection -> connection.info("commandstats"));
            Object dbSize = redisTemplate.execute((RedisCallback<Object>) connection -> connection.dbSize());

            result.setInfo(info);
            result.setDbSize(dbSize);

            List<Map<String, String>> pieList = new ArrayList<>();
            commandStats.stringPropertyNames().forEach(key -> {
                Map<String, String> data = new HashMap<>(2);
                String property = commandStats.getProperty(key);
                data.put("name", StringUtils.removeStart(key, "cmdstat_"));
                data.put("value", StringUtils.substringBetween(property, "calls=", ",usec"));
                pieList.add(data);
            });
            result.setCommandStats(pieList);
        } else {
            // Standalone mode (Caffeine)
            Properties info = new Properties();
            info.setProperty("redis_version", "N/A (Caffeine)");
            info.setProperty("connected_clients", "1");
            info.setProperty("used_memory_human", "N/A");
            result.setInfo(info);
            result.setDbSize(0);
            result.setCommandStats(new ArrayList<>());
        }
        
        return Result.success(result);
    }

    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @Operation(summary = "获取缓存名称列表", description = "获取系统定义的缓存名称列表")
    @GetMapping("/getNames")
    public Result<List<SysCacheVO>> cache()
    {
        List<SysCacheVO> list = new ArrayList<>();
        for (SysCache cache : caches) {
            list.add(new SysCacheVO(cache.getCacheName(), cache.getRemark()));
        }
        return Result.success(list);
    }

    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @Operation(summary = "获取缓存键名列表", description = "根据缓存名称获取键名列表")
    @GetMapping("/getKeys/{cacheName}")
    public Result<List<SysCacheVO>> getCacheKeys(@Parameter(description = "缓存名称", required = true) @PathVariable String cacheName)
    {
        Collection<String> cacheKeys = cacheService.keys(cacheName + "*");
        List<SysCacheVO> list = new ArrayList<SysCacheVO>();
        if (StringUtils.isNotEmpty(cacheKeys))
        {
            for (String cacheKey : cacheKeys)
            {
                SysCacheVO sysCache = new SysCacheVO(cacheName, cacheKey, "");
                sysCache.setCacheKey(cacheKey);
                sysCache.setTtl(String.valueOf(cacheService.getExpire(cacheKey)));
                list.add(sysCache);
            }
        }
        return Result.success(list);
    }

    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @Operation(summary = "获取缓存内容", description = "根据缓存名称和键名获取缓存内容")
    @GetMapping("/getValue/{cacheName}/{cacheKey}")
    public Result<SysCacheVO> getCacheValue(@Parameter(description = "缓存名称", required = true) @PathVariable String cacheName, 
                                            @Parameter(description = "缓存键名", required = true) @PathVariable String cacheKey)
    {
        Object cacheValue = cacheService.get(cacheKey);
        SysCacheVO sysCache = new SysCacheVO(cacheName, cacheKey, cacheValue == null ? "" : cacheValue.toString());
        sysCache.setTtl(String.valueOf(cacheService.getExpire(cacheKey)));
        return Result.success(sysCache);
    }

    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @Operation(summary = "清理缓存名称", description = "根据缓存名称清理缓存")
    @DeleteMapping("/clearCacheName/{cacheName}")
    public Result<Void> clearCacheName(@Parameter(description = "缓存名称", required = true) @PathVariable String cacheName)
    {
        Collection<String> keys = cacheService.keys(cacheName + "*");
        cacheService.delete(keys);
        return Result.success();
    }

    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @Operation(summary = "清理缓存键名", description = "根据缓存键名清理缓存")
    @DeleteMapping("/clearCacheKey/{cacheKey}")
    public Result<Void> clearCacheKey(@Parameter(description = "缓存键名", required = true) @PathVariable String cacheKey)
    {
        cacheService.delete(cacheKey);
        return Result.success();
    }

    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @Operation(summary = "清理全部缓存", description = "清理所有Redis缓存")
    @DeleteMapping("/clearCacheAll")
    public Result<Void> clearCacheAll()
    {
        Collection<String> keys = cacheService.keys("*");
        cacheService.delete(keys);
        return Result.success();
    }
}


