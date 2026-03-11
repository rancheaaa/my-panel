package com.cq.panel.admin.server.config;

import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * 线程池配置
 *
 * @author cq
 **/
@Configuration
public class ThreadPoolConfig
{
    // 核心线程池大小
    private int corePoolSize = 50;

    @Bean(name = "threadPoolTaskExecutor")
    public TaskExecutor threadPoolTaskExecutor()
    {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("virtual-task-");
        executor.setVirtualThreads(true);
        return executor;
    }

    /**
     * 配置 Quartz 使用虚拟线程
     */
    @Bean
    public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer()
    {
        return schedulerFactoryBean -> {
            SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("quartz-virtual-");
            executor.setVirtualThreads(true);
            schedulerFactoryBean.setTaskExecutor(executor);
        };
    }
}
