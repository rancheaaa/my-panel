package com.cq.panel.admin.server.config;

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
}
