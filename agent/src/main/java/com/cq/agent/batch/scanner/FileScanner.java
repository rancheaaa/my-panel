package com.cq.agent.batch.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件扫描器 - 按spec.md 4.5设计实现
 * 
 * 功能：
 * 1. 递归扫描指定目录
 * 2. 应用include_patterns通配符过滤
 * 3. 应用exclude_patterns通配符排除
 * 4. 收集文件元信息（文件名、大小、修改时间、绝对路径）
 * 5. 限制最大扫描文件数
 * 6. 容错处理：跳过无法访问的文件
 */
public class FileScanner {

    private static final Logger log = LoggerFactory.getLogger(FileScanner.class);

    /**
     * 扫描指定目录的文件
     *
     * @param rootDir       根目录路径
     * @param includePatterns 包含模式列表（如["*.log"]），null表示不过滤
     * @param excludePatterns 排除模式列表（如["debug*"]），null表示不排除
     * @param maxFiles      最大扫描文件数，0或null表示无限制
     * @return 扫描到的文件列表
     * @throws IllegalArgumentException 如果目录不存在
     */
    public List<ScannedFile> scan(String rootDir, List<String> includePatterns,
                                   List<String> excludePatterns, Integer maxFiles) {
        Path rootPath = Paths.get(rootDir).toAbsolutePath().normalize();

        // 验证目录存在且是目录
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("目录不存在或不是有效目录: " + rootPath);
        }

        List<ScannedFile> result = new ArrayList<>();
        int maxLimit = (maxFiles != null && maxFiles > 0) ? maxFiles : Integer.MAX_VALUE;

        // 构建过滤器
        Predicate<Path> fileFilter = buildFileFilter(includePatterns, excludePatterns);

        try (Stream<Path> pathStream = Files.walk(rootPath)) {
            pathStream
                .filter(Files::isRegularFile)
                .filter(fileFilter)
                .limit(maxLimit)
                .forEach(path -> {
                    try {
                        ScannedFile scannedFile = createScannedFile(path);
                        result.add(scannedFile);
                    } catch (Exception e) {
                        log.warn("⚠️  跳过无法读取的文件: {}, error={}", path, e.getMessage());
                    }
                });
        } catch (IOException e) {
            log.error("❌ 扫描目录失败: dir={}, error={}", rootDir, e.getMessage());
        }

        log.info("📁 文件扫描完成: dir={}, found={} files", rootDir, result.size());
        return result;
    }

    /**
     * 构建文件过滤器
     */
    private Predicate<Path> buildFileFilter(List<String> includePatterns, List<String> excludePatterns) {
        Predicate<Path> filter = p -> true;

        // 应用include过滤器
        if (includePatterns != null && !includePatterns.isEmpty()) {
            Predicate<Path> includeFilter = createWildcardFilter(includePatterns);
            filter = filter.and(includeFilter);
        }

        // 应用exclude过滤器
        if (excludePatterns != null && !excludePatterns.isEmpty()) {
            Predicate<Path> excludeFilter = createWildcardFilter(excludePatterns);
            filter = filter.and(excludeFilter.negate());
        }

        return filter;
    }

    /**
     * 创建通配符过滤器
     */
    private Predicate<Path> createWildcardFilter(List<String> patterns) {
        return path -> {
            String fileName = path.getFileName().toString();
            for (String pattern : patterns) {
                if (matchesWildcard(fileName, pattern)) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * 简单的通配符匹配（支持*和?）
     */
    private boolean matchesWildcard(String text, String pattern) {
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
        return text.matches(regex);
    }

    /**
     * 创建扫描结果对象
     */
    private ScannedFile createScannedFile(Path filePath) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);

        ScannedFile scannedFile = new ScannedFile();
        scannedFile.setFileName(filePath.getFileName().toString());
        scannedFile.setFileSize(attrs.size());
        scannedFile.setLastModified(attrs.lastModifiedTime().toMillis());
        scannedFile.setAbsolutePath(filePath.toAbsolutePath().toString());

        return scannedFile;
    }

    /**
     * 扫描结果数据类
     */
    public static class ScannedFile {
        private String fileName;
        private long fileSize;
        private long lastModified;
        private String absolutePath;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }

        public long getLastModified() { return lastModified; }
        public void setLastModified(long lastModified) { this.lastModified = lastModified; }

        public String getAbsolutePath() { return absolutePath; }
        public void setAbsolutePath(String absolutePath) { this.absolutePath = absolutePath; }

        @Override
        public String toString() {
            return "ScannedFile{fileName='" + fileName + "', size=" + fileSize +
                   ", modified=" + lastModified + ", path='" + absolutePath + "'}";
        }
    }
}
