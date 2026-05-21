package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.scanner.ScannedFile;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.TransferConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 目标路径计算工具
 * 统一 BatchUploadListener 和 BatchTaskSchedulerUploaderDecorator 中的路径计算逻辑，
 * 避免两处维护相同逻辑导致不一致
 */
public final class TargetPathComputer {

    private static final Logger log = LoggerFactory.getLogger(TargetPathComputer.class);

    private TargetPathComputer() {
    }

    /**
     * 计算目标路径（支持目录结构保持）
     *
     * @param sourceDir      源目录
     * @param originalPath   文件原始绝对路径
     * @param fileName       文件名
     * @param targetDir      目标目录
     * @param transferConfig 传输配置（可为null）
     * @return 计算后的目标路径
     */
    public static String compute(String sourceDir, String originalPath, String fileName,
            String targetDir, TransferConfig transferConfig) {
        if (targetDir == null) {
            return null;
        }

        boolean preserveDir = transferConfig != null && transferConfig.isPreserveDirStructure();

        String relativePath;
        if (preserveDir) {
            if (sourceDir != null && !sourceDir.trim().isEmpty()) {
                String absPath = normalizePath(originalPath);
                String normSourceDir = normalizePath(sourceDir);

                if (absPath.startsWith(normSourceDir)) {
                    relativePath = absPath.substring(normSourceDir.length());
                    if (relativePath.startsWith("/")) {
                        relativePath = relativePath.substring(1);
                    }
                } else {
                    log.warn("preserveDirStructure=true但sourceDir不匹配: sourceDir={}, filePath={}",
                            sourceDir, originalPath);
                    relativePath = fileName;
                }
            } else {
                log.warn("preserveDirStructure=true但sourceDir为空，使用文件名");
                relativePath = fileName;
            }
        } else {
            relativePath = fileName;
        }

        String separator = targetDir.endsWith("/") || targetDir.endsWith("\\") ? "" : "/";
        return targetDir + separator + relativePath;
    }

    /**
     * 统一路径分隔符为正斜杠，确保跨平台比较一致
     */
    private static String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        return path.replace("\\", "/").trim();
    }
}
