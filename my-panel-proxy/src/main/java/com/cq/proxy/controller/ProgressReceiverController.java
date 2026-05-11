package com.cq.proxy.controller.batch;

import com.cq.proxy.service.batch.ProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 进度接收控制器
 * 接收Agent上报的子任务进度、完成、失败、重试状态
 */
@RestController
@RequestMapping("/api/batch/subtask")
public class ProgressReceiverController {

    private static final Logger log = LoggerFactory.getLogger(ProgressReceiverController.class);

    private final ProgressService progressService;

    public ProgressReceiverController(ProgressService progressService) {
        this.progressService = progressService;
    }

    /**
     * 接收单个子任务进度更新
     */
    @PostMapping("/progress")
    public ResponseEntity<Map<String, Object>> receiveProgress(@RequestBody Map<String, Object> progressData) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Long subtaskId = ((Number) progressData.get("subtaskId")).longValue();
            int transferredBytes = ((Number) progressData.get("transferredBytes")).intValue();
            int totalBytes = ((Number) progressData.get("totalBytes")).intValue();
            
            Long timestamp = progressData.containsKey("timestamp") ? 
                ((Number) progressData.get("timestamp")).longValue() : System.currentTimeMillis();
            
            if (progressService.isStaleData(subtaskId, timestamp)) {
                result.put("code", 200);
                result.put("msg", "stale data ignored");
                return ResponseEntity.ok(result);
            }
            
            progressService.updateProgress(subtaskId, transferredBytes, totalBytes);
            
            result.put("code", 200);
            result.put("msg", "success");
            log.info("✅ 子任务进度更新: subtaskId={}, progress={}/{}", 
                subtaskId, transferredBytes, totalBytes);
                
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            log.error("❌ 进度更新失败: {}", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 批量接收多个子任务进度
     */
    @PostMapping("/progress/batch")
    public ResponseEntity<Map<String, Object>> receiveBatchProgress(
            @RequestBody List<Map<String, Object>> batchData) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            progressService.batchUpdateProgress(batchData);
            
            result.put("code", 200);
            result.put("data", Map.of("updatedCount", batchData.size()));
            log.info("✅ 批量进度更新: count={}", batchData.size());
            
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            log.error("❌ 批量进度更新失败: {}", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 接收子任务完成通知
     */
    @PostMapping("/complete")
    public ResponseEntity<Map<String, Object>> receiveComplete(@RequestBody Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Long subtaskId = ((Number) data.get("subtaskId")).longValue();
            String targetPath = (String) data.get("targetPath");
            
            progressService.markCompleted(subtaskId, targetPath);
            
            result.put("code", 200);
            result.put("data", Map.of("status", "COMPLETED"));
            log.info("✅ 子任务完成: subtaskId={}, path={}", subtaskId, targetPath);
            
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            log.error("❌ 完成通知处理失败: {}", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 接收子任务失败通知
     */
    @PostMapping("/failed")
    public ResponseEntity<Map<String, Object>> receiveFailed(@RequestBody Map<String, Object> errorData) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Long subtaskId = ((Number) errorData.get("subtaskId")).longValue();
            String errorCode = (String) errorData.get("errorCode");
            String errorMessage = (String) errorData.get("errorMessage");
            
            progressService.markFailed(subtaskId, errorCode, errorMessage);
            
            result.put("code", 200);
            result.put("data", Map.of("status", "FAILED"));
            log.warn("⚠️  子任务失败: subtaskId={}, error=[{}]: {}", 
                subtaskId, errorCode, errorMessage);
            
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            log.error("❌ 失败通知处理失败: {}", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 接收重试中状态通知
     */
    @PostMapping("/retrying")
    public ResponseEntity<Map<String, Object>> receiveRetrying(@RequestBody Map<String, Object> retryData) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Long subtaskId = ((Number) retryData.get("subtaskId")).longValue();
            
            long nextRetryAt = progressService.scheduleNextRetry(subtaskId);
            
            result.put("code", 200);
            result.put("data", Map.of(
                "status", "RETRYING",
                "nextRetryAt", nextRetryAt
            ));
            log.info("🔄 子任务重试中: subtaskId={}, nextRetryAt={}", subtaskId, nextRetryAt);
            
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            log.error("❌ 重试通知处理失败: {}", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
}
