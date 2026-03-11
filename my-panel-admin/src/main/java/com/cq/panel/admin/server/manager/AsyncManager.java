package com.cq.panel.admin.server.manager;

import com.cq.panel.admin.server.common.utils.Threads;
import com.cq.panel.admin.server.common.utils.spring.SpringUtils;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 异步任务管理器
 * 
 * @author cq
 */
public class AsyncManager
{
    /**
     * 操作延迟10毫秒
     */
    private final int OPERATE_DELAY_TIME = 10;

    /**
     * 调度器（使用单线程平台线程）
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("async-scheduler").factory()
    );

    /**
     * 执行器（使用虚拟线程）
     */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 单例模式
     */
    private AsyncManager(){}

    private static AsyncManager me = new AsyncManager();

    public static AsyncManager me()
    {
        return me;
    }

    /**
     * 执行任务
     * 
     * @param task 任务
     */
    public void execute(TimerTask task)
    {
        scheduler.schedule(() -> executor.execute(task), OPERATE_DELAY_TIME, TimeUnit.MILLISECONDS);
    }

    /**
     * 停止任务线程池
     */
    public void shutdown()
    {
        Threads.shutdownAndAwaitTermination(executor);
        Threads.shutdownAndAwaitTermination(scheduler);
    }
}
