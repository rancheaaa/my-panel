package com.cq.agent.batch.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件去重器
 * 基于文件名、大小、修改时间判断是否需要传输
 * 避免重复传输相同文件
 */
public class FileDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(FileDeduplicator.class);

    private final Map<String, FileSignature> processedFiles = new HashMap<>();

    /**
     * 记录已处理的文件
     */
    public void recordProcessed(FileInfo fileInfo) {
        String key = generateKey(fileInfo);
        FileSignature signature = new FileSignature(
            fileInfo.getSize(),
            fileInfo.getLastModified(),
            LocalDate.now(ZoneId.systemDefault())
        );
        
        processedFiles.put(key, signature);
        
        log.debug("📝 记录已处理: {}", fileInfo.getFileName());
    }

    /**
     * 判断文件是否应该被包含（未被处理过或已修改）
     * @return true表示应包含，false表示应跳过
     */
    public boolean shouldInclude(FileInfo fileInfo) {
        String key = generateKey(fileInfo);
        FileSignature existing = processedFiles.get(key);
        
        if (existing == null) {
            log.debug("✅ 新文件: {}", fileInfo.getFileName());
            return true;
        }
        
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        
        if (existing.processDate.equals(today)) {
            if (fileInfo.getSize() == existing.size && 
                fileInfo.getLastModified() >= existing.lastModified) {
                log.debug("⏭️  同日未变文件: {}", fileInfo.getFileName());
                return false;
            }
        }
        
        if (fileInfo.getSize() != existing.size || 
            fileInfo.getLastModified() > existing.lastModified) {
            log.info("🔄 文件已修改，重新包含: {}", fileInfo.getFileName());
            return true;
        }
        
        log.debug("⏭️  跳过未变文件: {}", fileInfo.getFileName());
        return false;
    }

    /**
     * 清除所有记录
     */
    public void clear() {
        processedFiles.clear();
        log.info("🗑️  已清除所有去重记录");
    }

    /**
     * 获取已记录的文件数量
     */
    public int getRecordedCount() {
        return processedFiles.size();
    }

    // ==================== 内部方法 ====================

    private String generateKey(FileInfo fileInfo) {
        return fileInfo.getFileName() + "_" + fileInfo.getPath().getParent();
    }

    /**
     * 文件签名（用于比较）
     */
    private static class FileSignature {
        final long size;
        final long lastModified;
        final LocalDate processDate;

        FileSignature(long size, long lastModified, LocalDate processDate) {
            this.size = size;
            this.lastModified = lastModified;
            this.processDate = processDate;
        }
    }
}
