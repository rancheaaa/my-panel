package com.cq.agent.batch.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 目录扫描器
 * 递归扫描目录，根据Include/Exclude模式过滤文件
 */
public class DirectoryScanner {

    private static final Logger log = LoggerFactory.getLogger(DirectoryScanner.class);

    /**
     * 扫描目录
     * @param sourceDir 源目录路径
     * @param includePatterns 包含模式列表（如 ["*.log", "*.txt"]）
     * @param excludePatterns 排除模式列表（如 ["debug*", "temp*"]）
     * @param maxFiles 最大返回文件数限制
     * @return 匹配的文件信息列表
     */
    public List<FileInfo> scan(String sourceDir, List<String> includePatterns, 
                                List<String> excludePatterns, int maxFiles) {
        List<FileInfo> results = new ArrayList<>();
        
        Path dirPath = Paths.get(sourceDir);
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            log.error("❌ 目录不存在或不是目录: {}", sourceDir);
            throw new IllegalArgumentException("Directory not found: " + sourceDir);
        }
        
        try {
            Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (results.size() >= maxFiles) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    
                    String fileName = file.getFileName().toString();
                    
                    if (!matchesPattern(fileName, includePatterns)) {
                        return FileVisitResult.CONTINUE;
                    }
                    
                    if (isExcluded(fileName, excludePatterns)) {
                        return FileVisitResult.CONTINUE;
                    }
                    
                    FileInfo fileInfo = new FileInfo(
                        file,
                        fileName,
                        attrs.size(),
                        attrs.lastModifiedTime().toMillis()
                    );
                    
                    results.add(fileInfo);
                    return FileVisitResult.CONTINUE;
                }
            });
            
            log.info("✅ 目录扫描完成: dir={}, matched={}", sourceDir, results.size());
            
        } catch (IOException e) {
            log.error("❌ 目录扫描失败: {}", sourceDir, e);
            throw new RuntimeException("Scan failed", e);
        }
        
        return results;
    }

    // ==================== 内部方法 ====================

    /**
     * 检查文件名是否匹配包含模式
     */
    private boolean matchesPattern(String fileName, List<String> patterns) {
        if (patterns == null || patterns.isEmpty() || patterns.contains("*")) {
            return true;
        }
        
        for (String pattern : patterns) {
            if (matchesWildcard(fileName, pattern)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 检查文件名是否被排除
     */
    private boolean isExcluded(String fileName, List<String> excludePatterns) {
        if (excludePatterns == null || excludePatterns.isEmpty()) {
            return false;
        }
        
        for (String pattern : excludePatterns) {
            if (matchesWildcard(fileName, pattern)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 简化的通配符匹配
     * 支持: * (任意字符), ? (单个字符)
     */
    private boolean matchesWildcard(String text, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return true;
        }
        
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
            
        return text.matches(regex);
    }
}
