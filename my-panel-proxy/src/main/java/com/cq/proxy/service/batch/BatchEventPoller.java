package com.cq.proxy.service.batch;

import com.cq.proxy.repository.entity.BatchSyncEvent;
import com.cq.proxy.repository.mapper.BatchSyncEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 批量事件轮询器
 * 定时拉取PENDING事件并分发给EventHandler处理
 * 符合spec.md设计要求：
 * - 每3-5秒轮询一次
 * - 恢复卡住的PROCESSING事件
 * - 清理过期事件
 */
@Service
public class BatchEventPoller {

    private static final Logger log = LoggerFactory.getLogger(BatchEventPoller.class);

    private static final int BATCH_SIZE = 10;
    private static final int PROCESSING_TIMEOUT_MINUTES = 5;

    private final BatchSyncEventMapper eventMapper;
    private final BatchEventHandler eventHandler;

    public BatchEventPoller(BatchSyncEventMapper eventMapper, BatchEventHandler eventHandler) {
        this.eventMapper = eventMapper;
        this.eventHandler = eventHandler;
    }

    @Scheduled(fixedDelayString = "${batch.sync.poll-interval-ms:3000}")
    public void poll() {
        try {
            // 1. 清理过期事件
            eventHandler.cleanupExpiredEvents();

            // 2. 恢复卡住的事件
            recoverStuckEvents();

            // 3. 轮询待处理事件
            pollPendingEvents();
        } catch (Exception e) {
            log.error("事件轮询异常", e);
        }
    }

    private void pollPendingEvents() {
        List<BatchSyncEvent> events = eventMapper.selectPendingEvents(BATCH_SIZE);

        if (events == null || events.isEmpty()) {
            return;
        }

        log.info("拉取到{}个待处理事件", events.size());

        for (BatchSyncEvent event : events) {
            try {
                eventHandler.handleEvent(event);
            } catch (Exception e) {
                log.error("处理事件异常: eventId={}", event.getId(), e);
            }
        }
    }

    private void recoverStuckEvents() {
        try {
            Date threshold = new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(PROCESSING_TIMEOUT_MINUTES));
            List<BatchSyncEvent> stuckEvents = eventMapper.selectStuckProcessingEvents(threshold);

            if (stuckEvents != null && !stuckEvents.isEmpty()) {
                for (BatchSyncEvent event : stuckEvents) {
                    eventMapper.updateStatusToPending(event.getId());
                }
                log.warn("恢复了{}个卡住的PROCESSING事件", stuckEvents.size());
            }
        } catch (Exception e) {
            log.error("恢复卡住事件异常", e);
        }
    }
}
