package com.cq.proxy.service.batch;

import com.cq.proxy.repository.entity.BatchSyncEvent;
import com.cq.proxy.repository.mapper.BatchSyncEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 批量事件处理器
 * 处理事件队列中的任务事件
 * 符合spec.md设计要求：
 * - 重试策略：指数退避（1s, 2s, 4s, 8s...上限60s）
 * - 过期事件清理
 * - 卡住事件恢复
 * - 推送配置到Agent并验证configPersisted标志
 */
@Component
public class BatchEventHandler {

    private static final Logger log = LoggerFactory.getLogger(BatchEventHandler.class);
    public static final int MAX_RETRY_COUNT = 10;
    private static final long MAX_RETRY_DELAY_MS = TimeUnit.SECONDS.toMillis(60);

    private final BatchSyncEventMapper eventMapper;
    private final ObjectMapper objectMapper;
    private final AgentPushService agentPushService;

    public BatchEventHandler(BatchSyncEventMapper eventMapper, ObjectMapper objectMapper, AgentPushService agentPushService) {
        this.eventMapper = eventMapper;
        this.objectMapper = objectMapper;
        this.agentPushService = agentPushService;
    }

    /**
     * 处理单个事件
     */
    @Transactional
    public void handleEvent(BatchSyncEvent event) {
        try {
            // 标记为PROCESSING
            event.setStatus("PROCESSING");
            event.setStartedAt(new Date());
            eventMapper.updateStatusToProcessing(event);

            boolean pushSuccess = switch (event.getEventType()) {
                case "TASK_CREATED" -> agentPushService.pushConfigToAgent(event.getSourceAgentId(), event.getPayload());
                case "TASK_UPDATED" -> agentPushService.pushConfigToAgent(event.getSourceAgentId(), event.getPayload());
                case "TASK_STATUS_CHANGED" ->
                        agentPushService.pushConfigToAgent(event.getSourceAgentId(), event.getPayload());
                case "TASK_DELETED" -> agentPushService.pushDeleteToAgent(event.getSourceAgentId(), event.getPayload());
                default -> {
                    log.warn("⚠️  未知事件类型: {}", event.getEventType());
                    throw new IllegalArgumentException("未知事件类型: " + event.getEventType());
                }
            };

            if (!pushSuccess) {
                throw new RuntimeException("Agent未确认持久化");
            }

            // 标记为COMPLETED
            eventMapper.updateStatusToCompleted(event.getId());
            log.info("✅ 事件处理完成: eventId={}, type={}", event.getId(), event.getEventType());

        } catch (Exception e) {
            log.error("❌ 事件处理失败: eventId={}, error={}", event.getId(), e.getMessage());
            handleFailure(event, e);
        }
    }

    /**
     * 清理过期事件
     */
    public void cleanupExpiredEvents() {
        try {
            Date now = new Date();
            List<BatchSyncEvent> expiredEvents = eventMapper.selectExpiredEvents(now);

            for (BatchSyncEvent event : expiredEvents) {
                eventMapper.updateStatusToFailed(event.getId(), "事件过期");
                log.warn("🧹 过期事件已清理: eventId={}", event.getId());
            }

            if (!expiredEvents.isEmpty()) {
                log.info("🧹 过期事件清理完成: count={}", expiredEvents.size());
            }
        } catch (Exception e) {
            log.error("❌ 过期事件清理失败: error={}", e.getMessage());
        }
    }

    /**
     * 恢复卡住的PROCESSING事件
     */
    public void recoverStuckProcessing() {
        try {
            // 超过5分钟仍在PROCESSING状态的事件视为卡住
            Date threshold = new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
            List<BatchSyncEvent> stuckEvents = eventMapper.selectStuckProcessingEvents(threshold);

            for (BatchSyncEvent event : stuckEvents) {
                eventMapper.updateStatusToPending(event.getId());
                log.warn("🔄 卡住事件已恢复: eventId={}", event.getId());
            }

            if (!stuckEvents.isEmpty()) {
                log.info("🔄 卡住事件恢复完成: count={}", stuckEvents.size());
            }
        } catch (Exception e) {
            log.error("❌ 卡住事件恢复失败: error={}", e.getMessage());
        }
    }

    /**
     * 计算重试延迟 - 指数退避
     * 1s, 2s, 4s, 8s...上限60s
     */
    public long calculateRetryDelay(int retryCount) {
        long delay = (long) (TimeUnit.SECONDS.toMillis(1) * Math.pow(2, retryCount - 1));
        return Math.min(delay, MAX_RETRY_DELAY_MS);
    }

    // ==================== 内部方法 ====================

    private void handleFailure(BatchSyncEvent event, Exception e) {
        int newRetryCount = event.getRetryCount() + 1;
        String errorMessage = e.getMessage();

        if (newRetryCount >= MAX_RETRY_COUNT) {
            // 超过最大重试次数，标记为FAILED
            eventMapper.updateStatusToFailed(event.getId(),
                "超过最大重试次数: " + errorMessage);
            log.error("❌ 事件最终失败: eventId={}, retryCount={}",
                event.getId(), newRetryCount);
        } else {
            // 计算下次重试时间（指数退避）
            long delay = calculateRetryDelay(newRetryCount);
            Date nextRetryAt = new Date(System.currentTimeMillis() + delay);

            eventMapper.updateRetry(event.getId(), nextRetryAt, errorMessage);
            log.warn("🔄 事件处理失败，已调度重试: eventId={}, retryCount={}, nextRetryAt={}",
                event.getId(), newRetryCount, nextRetryAt);
        }
    }
}
