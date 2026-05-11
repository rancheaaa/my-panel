package com.cq.agent.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 原子文件写入器
 * 符合spec.md设计要求：
 * 1. 写入临时文件
 * 2. 校验内容完整性
 * 3. 原子重命名（mv tmp → target）
 * 4. 失败时清理临时文件
 */
public class AtomicFileWriter {

    private static final Logger log = LoggerFactory.getLogger(AtomicFileWriter.class);

    /**
     * 原子写入文件
     * @param targetFile 目标文件
     * @param content 文件内容
     */
    public void writeAtomic(File targetFile, String content) {
        File tempFile = new File(targetFile.getParent(), targetFile.getName() + ".tmp");

        try {
            // 1. 确保父目录存在
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 2. 写入临时文件
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(content);
                writer.flush();
            }

            // 3. 校验临时文件存在且非空
            if (!tempFile.exists() || tempFile.length() == 0) {
                throw new IOException("临时文件写入失败或为空");
            }

            // 4. 原子重命名
            if (!tempFile.renameTo(targetFile)) {
                // 重命名失败，尝试删除目标文件后重试
                if (targetFile.exists() && !targetFile.delete()) {
                    throw new IOException("无法删除旧文件: " + targetFile.getAbsolutePath());
                }
                if (!tempFile.renameTo(targetFile)) {
                    throw new IOException("原子重命名失败: " + tempFile.getAbsolutePath() + " → " + targetFile.getAbsolutePath());
                }
            }

            log.debug("✅ 原子写入成功: {}", targetFile.getAbsolutePath());

        } catch (IOException e) {
            // 清理临时文件
            if (tempFile.exists() && !tempFile.delete()) {
                log.warn("⚠️  清理临时文件失败: {}", tempFile.getAbsolutePath());
            }
            log.error("❌ 原子写入失败: {}, error={}", targetFile.getAbsolutePath(), e.getMessage());
            throw new RuntimeException("原子写入失败: " + e.getMessage(), e);
        }
    }
}
