package com.cq.panel.admin.server.manager;

import com.cq.panel.admin.server.common.utils.Threads;
import java.util.TimerTask;
import java.util.concurrent.*;

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
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1,
            Thread.ofPlatform().name("async-scheduler").factory()
    );

    /**
     * 执行器（使用虚拟线程）
     */
    private final ExecutorService executor = createVirtualThreadExecutor();

    /**
     * 单例模式
     */
    private AsyncManager(){}

    private static final AsyncManager me = new AsyncManager();

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

    /**
     * 创建虚拟线程执行器
     */
    private static ExecutorService createVirtualThreadExecutor() {
        return new ThreadPoolExecutor(
                0, Integer.MAX_VALUE,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                Thread.ofVirtual().name("async-virtual-", 0).factory());
    }
}