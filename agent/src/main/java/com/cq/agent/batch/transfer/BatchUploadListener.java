package com.cq.agent.batch.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * 上传监听器
 * 监控文件上传过程，提供成功/失败/进度回调
 * 符合spec.md设计要求，支持完整的进度信息传递
 */
public class BatchUploadListener {

    private static final Logger log = LoggerFactory.getLogger(BatchUploadListener.class);

    private final List<BiConsumer<Long, String>> successListeners = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<Long, String>> failureListeners = new CopyOnWriteArrayList<>();
    private final List<ProgressCallback> progressListeners = new CopyOnWriteArrayList<>();

    /**
     * 注册成功回调
     */
    public void onSuccess(BiConsumer<Long, String> listener) {
        successListeners.add(listener);
    }

    /**
     * 注册失败回调
     */
    public void onFailure(BiConsumer<Long, String> listener) {
        failureListeners.add(listener);
    }

    /**
     * 注册进度回调
     */
    public void onProgress(ProgressCallback listener) {
        progressListeners.add(listener);
    }

    /**
     * 通知上传成功
     */
    public void notifySuccess(Long subtaskId, String targetPath) {
        log.info("✅ 上传成功: subtaskId={}, path={}", subtaskId, targetPath);

        for (BiConsumer<Long, String> listener : successListeners) {
            try {
                listener.accept(subtaskId, targetPath);
            } catch (Exception e) {
                log.error("❌ 成功回调异常: subtaskId={}", subtaskId, e);
            }
        }
    }

    /**
     * 通知上传失败
     */
    public void notifyFailure(Long subtaskId, String error) {
        log.warn("⚠️  上传失败: subtaskId={}, error={}", subtaskId, error);

        for (BiConsumer<Long, String> listener : failureListeners) {
            try {
                listener.accept(subtaskId, error);
            } catch (Exception e) {
                log.error("❌ 失败回调异常: subtaskId={}", subtaskId, e);
            }
        }
    }

    /**
     * 通知进度更新 - 旧版本兼容（只有bytes信息）
     */
    public void notifyProgress(Long subtaskId, long transferredBytes, long totalBytes) {
        notifyProgress(subtaskId, transferredBytes, totalBytes, 0, 0, 0);
    }

    /**
     * 通知进度更新 - 完整版本（符合spec.md要求）
     * @param subtaskId 子任务ID
     * @param transferredBytes 已传输字节数
     * @param totalBytes 总字节数
     * @param transferredChunks 已传输分块数
     * @param totalChunks 总分块数
     * @param speedBytesPerSec 当前传输速率(字节/秒)
     */
    public void notifyProgress(Long subtaskId, long transferredBytes, long totalBytes,
                               int transferredChunks, int totalChunks, long speedBytesPerSec) {
        if (!progressListeners.isEmpty()) {
            int percent = totalBytes > 0 ? (int) ((transferredBytes * 100) / totalBytes) : 0;
            log.debug("📊 进度: subtaskId={}, {}%, {}/{} bytes, chunks={}/{}",
                subtaskId, percent, transferredBytes, totalBytes, transferredChunks, totalChunks);

            for (ProgressCallback listener : progressListeners) {
                try {
                    listener.onProgress(subtaskId, transferredBytes, totalBytes,
                        transferredChunks, totalChunks, speedBytesPerSec);
                } catch (Exception e) {
                    log.error("❌ 进度回调异常: subtaskId={}", subtaskId, e);
                }
            }
        }
    }

    /**
     * 清除所有监听器
     */
    public void clear() {
        successListeners.clear();
        failureListeners.clear();
        progressListeners.clear();
        log.info("🗑️  所有监听器已清除");
    }

    /**
     * 获取已注册的监听器数量
     */
    public int getListenerCount() {
        return successListeners.size() + failureListeners.size() + progressListeners.size();
    }

    @FunctionalInterface
    public interface ProgressCallback {
        /**
         * 进度回调 - 完整版本（符合spec.md）
         */
        void onProgress(Long subtaskId, long transferredBytes, long totalBytes,
                        int transferredChunks, int totalChunks, long speedBytesPerSec);
    }
}
