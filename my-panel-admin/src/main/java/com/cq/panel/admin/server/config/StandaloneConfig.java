package com.cq.panel.admin.server.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

/**
 * Standalone mode configuration (SQLite + Local Cache)
 *
 * @author ruoyi
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "app.mode", havingValue = "standalone")
public class StandaloneConfig
{
    @Value("${spring.datasource.sqlite.url:jdbc:sqlite:my-panel.db}")
    private String dataSourceUrl;

    /*
    @Bean
    @Primary
    public DataSource dataSource()
    {
        return DataSourceBuilder.create()
                .url(dataSourceUrl)
                .driverClassName("org.sqlite.JDBC")
                .build();
    }
    */

    @Bean
    public CacheManager cacheManager()
    {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                // Default expiration for @Cacheable caches
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(1000));
        return cacheManager;
    }
}
