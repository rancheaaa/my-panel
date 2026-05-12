package com.cq.agent.batch.scheduler;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.ScanConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TDD测试：验证BatchTaskSchedulerManager启动加载任务到Quartz是否符合spec.md设计
 * 核心要求：
 * 1. Agent启动时从本地加载所有RUNNING状态的任务到Quartz
 * 2. 支持Cron表达式调度
 * 3. 支持热启动、暂停、恢复、更新、删除
 * 4. 启动时立即执行一次扫描
 * 5. 失败重试机制
 */
@DisplayName("BatchTaskSchedulerManager - 符合spec.md设计")
class BatchTaskSchedulerManagerSpecTest {

    @Mock
    private ConfigFileManager configFileManager;

    @Mock
    private QuartzTaskScheduler quartzTaskScheduler;

    @InjectMocks
    private BatchTaskSchedulerManager schedulerManager;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        // 使用反射替换quartzTaskScheduler为mock
        java.lang.reflect.Field field = BatchTaskSchedulerManager.class.getDeclaredField("quartzTaskScheduler");
        field.setAccessible(true);
        field.set(schedulerManager, quartzTaskScheduler);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ==================== Red Phase: spec.md要求验证 ====================

    @Test
    @DisplayName("1. [spec.md] 启动时应加载所有RUNNING状态的任务")
    void testStartAllRunningTasks_shouldLoadRunningTasks() {
        // Given: 2个RUNNING任务，1个PAUSED任务
        AgentTaskConfig runningTask1 = createConfig(1L, "RUNNING", "0 */5 * * * ?");
        AgentTaskConfig runningTask2 = createConfig(2L, "RUNNING", "0 0 * * * ?");
        AgentTaskConfig pausedTask = createConfig(3L, "PAUSED", "0 0 * * * ?");

        when(configFileManager.loadAllTaskConfigs()).thenReturn(
            Arrays.asList(runningTask1, runningTask2, pausedTask)
        );

        // When
        schedulerManager.startAllRunningTasks();

        // Then: 只有RUNNING任务被启动
        verify(quartzTaskScheduler, times(2)).startTask(any(AgentTaskConfig.class), any(Runnable.class));
        verify(quartzTaskScheduler).startTask(argThat(c -> c.getTaskId().equals(1L)), any(Runnable.class));
        verify(quartzTaskScheduler).startTask(argThat(c -> c.getTaskId().equals(2L)), any(Runnable.class));

        System.out.println("✅ 启动加载验证: 2个RUNNING任务被启动，1个PAUSED被跳过");
    }

    @Test
    @DisplayName("2. [spec.md] 无配置时应空转")
    void testStartAllRunningTasks_noConfigs_shouldDoNothing() {
        when(configFileManager.loadAllTaskConfigs()).thenReturn(Collections.emptyList());

        schedulerManager.startAllRunningTasks();

        verify(quartzTaskScheduler, never()).startTask(any(), any());
        System.out.println("✅ 空配置验证: 无任务被启动");
    }

    @Test
    @DisplayName("3. [spec.md] 启动任务时应传入Cron表达式")
    void testStartTask_shouldPassCronExpression() {
        AgentTaskConfig config = createConfig(1L, "RUNNING", "0 */5 * * * ?");

        schedulerManager.startTask(config);

        verify(quartzTaskScheduler).startTask(argThat(c ->
            c.getTaskId().equals(1L) &&
            c.getScanConfig() != null &&
            "0 */5 * * * ?".equals(c.getScanConfig().getCronExpression())
        ), any(Runnable.class));

        System.out.println("✅ Cron表达式验证: 正确传递给Quartz");
    }

    @Test
    @DisplayName("4. [spec.md] 缺少Cron表达式的任务应跳过")
    void testStartTask_noCron_shouldSkip() {
        AgentTaskConfig config = createConfig(1L, "RUNNING", null);

        schedulerManager.startTask(config);

        verify(quartzTaskScheduler, never()).startTask(any(), any());
        System.out.println("✅ Cron校验验证: 缺少Cron的任务被跳过");
    }

    @Test
    @DisplayName("5. [spec.md] 应支持暂停任务")
    void testPauseTask_shouldCallQuartzPause() {
        schedulerManager.pauseTask(1L);

        verify(quartzTaskScheduler).pauseTask(1L);
        System.out.println("✅ 暂停验证: 调用Quartz暂停");
    }

    @Test
    @DisplayName("6. [spec.md] 应支持恢复任务")
    void testResumeTask_shouldCallQuartzResume() {
        schedulerManager.resumeTask(1L);

        verify(quartzTaskScheduler).resumeTask(1L);
        System.out.println("✅ 恢复验证: 调用Quartz恢复");
    }

    @Test
    @DisplayName("7. [spec.md] 应支持热更新任务")
    void testUpdateTask_shouldCallQuartzUpdate() {
        AgentTaskConfig config = createConfig(1L, "RUNNING", "0 */10 * * * ?");
        when(quartzTaskScheduler.isTaskRunning(1L)).thenReturn(true);

        schedulerManager.updateTask(config);

        verify(quartzTaskScheduler).updateTask(config);
        System.out.println("✅ 热更新验证: 调用Quartz更新");
    }

    @Test
    @DisplayName("8. [spec.md] 更新未运行任务时应启动它")
    void testUpdateTask_notRunning_shouldStart() {
        AgentTaskConfig config = createConfig(1L, "RUNNING", "0 */10 * * * ?");
        when(quartzTaskScheduler.isTaskRunning(1L)).thenReturn(false);

        schedulerManager.updateTask(config);

        verify(quartzTaskScheduler, never()).updateTask(any());
        verify(quartzTaskScheduler).startTask(eq(config), any(Runnable.class));
        System.out.println("✅ 更新未运行任务验证: 自动启动");
    }

    @Test
    @DisplayName("9. [spec.md] 应支持删除任务")
    void testDeleteTask_shouldCallQuartzDelete() {
        schedulerManager.deleteTask(1L);

        verify(quartzTaskScheduler).deleteTask(1L);
        System.out.println("✅ 删除验证: 调用Quartz删除");
    }

    @Test
    @DisplayName("10. [spec.md] 应支持检查任务运行状态")
    void testIsTaskRunning_shouldCallQuartzCheck() {
        when(quartzTaskScheduler.isTaskRunning(1L)).thenReturn(true);

        boolean running = schedulerManager.isTaskRunning(1L);

        assertTrue(running);
        verify(quartzTaskScheduler).isTaskRunning(1L);
        System.out.println("✅ 状态检查验证: 正确返回运行状态");
    }

    @Test
    @DisplayName("11. [spec.md] 关闭时应停止所有任务")
    void testShutdown_shouldCallQuartzShutdown() {
        schedulerManager.shutdown();

        verify(quartzTaskScheduler).shutdown();
        System.out.println("✅ 关闭验证: 停止所有Quartz任务");
    }

    // ==================== 辅助方法 ====================

    private AgentTaskConfig createConfig(Long taskId, String status, String cronExpression) {
        AgentTaskConfig config = new AgentTaskConfig();
        config.setTaskId(taskId);
        config.setTaskName("任务" + taskId);
        config.setStatus(status);
        if (cronExpression != null) {
            ScanConfig scanConfig = new ScanConfig();
            scanConfig.setCronExpression(cronExpression);
            config.setScanConfig(scanConfig);
        }
        config.setSourceAgentId("agent-001");
        config.setSourceDir("/var/log/app");
        config.setVersion(String.valueOf(1));
        return config;
    }
}
