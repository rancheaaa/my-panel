package com.cq.panel.admin.server.service.batch.util;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.*;

/**
 * 通配符冲突检测器
 * 
 * 用于检测两个批量传输任务的文件匹配范围是否存在重叠，
 * 防止同一文件被多个任务重复传输。
 */
public class WildcardConflictDetector {

    private static final Set<String> DANGEROUS_PATTERNS = Set.of(
        "..", "../", "/../"
    );

    public boolean hasConflict(String sourceDir1, List<String> includePatterns1, List<String> excludePatterns1,
                               String sourceDir2, List<String> includePatterns2, List<String> excludePatterns2) {
        validatePathSecurity(sourceDir1);
        validatePathSecurity(sourceDir2);
        validatePatternSecurity(includePatterns1);
        validatePatternSecurity(includePatterns2);
        
        if (includePatterns1 == null || includePatterns1.isEmpty() ||
            includePatterns2 == null || includePatterns2.isEmpty()) {
            return false;
        }
        
        for (String pattern1 : includePatterns1) {
            for (String pattern2 : includePatterns2) {
                if (hasPatternConflict(sourceDir1, pattern1, sourceDir2, pattern2)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private void validatePathSecurity(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        
        if (DANGEROUS_PATTERNS.stream().anyMatch(path::contains)) {
            throw new SecurityException("检测到路径穿越攻击: " + path);
        }
        
        try {
            Paths.get(path).normalize();
        } catch (InvalidPathException e) {
            throw new SecurityException("非法路径格式: " + path);
        }
    }
    
    private void validatePatternSecurity(List<String> patterns) {
        if (patterns == null) return;
        
        for (String pattern : patterns) {
            if (pattern != null && DANGEROUS_PATTERNS.stream().anyMatch(pattern::contains)) {
                throw new SecurityException("检测到通配符中的路径穿越攻击: " + pattern);
            }
        }
    }

    private boolean hasPatternConflict(String dir1, String pattern1, String dir2, String pattern2) {
        if (!dir1.equals(dir2)) {
            return false;
        }
        
        String normalizedP1 = normalizePattern(pattern1);
        String normalizedP2 = normalizePattern(pattern2);
        
        if (normalizedP1.equals(normalizedP2)) {
            return true;
        }
        
        boolean p1HasSubdir = normalizedP1.contains("/") || normalizedP1.contains(File.separator);
        boolean p2HasSubdir = normalizedP2.contains("/") || normalizedP2.contains(File.separator);
        
        if (p1HasSubdir && p2HasSubdir) {
            return subdirConflict(normalizedP1, normalizedP2);
        }
        
        if (!p1HasSubdir && !p2HasSubdir) {
            return flatConflict(normalizedP1, normalizedP2);
        }
        
        String flatPattern = p1HasSubdir ? normalizedP2 : normalizedP1;
        String subdirPattern = p1HasSubdir ? normalizedP1 : normalizedP2;
        
        return flatVsSubdirConflict(flatPattern, subdirPattern);
    }
    
    private String normalizePattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) return "*";
        return pattern.trim().replace("**", "*");
    }
    
    private boolean flatConflict(String p1, String p2) {
        if (p1.equals(p2)) return true;
        
        boolean p1Wild = p1.contains("*") || p1.contains("?");
        boolean p2Wild = p2.contains("*") || p2.contains("?");
        
        if (!p1Wild && !p2Wild) return false;
        
        if (!p1Wild || !p2Wild) return false;
        
        String ext1 = getExtension(p1);
        String ext2 = getExtension(p2);
        
        if (!ext1.isEmpty() && !ext2.isEmpty() && !ext1.equalsIgnoreCase(ext2)) {
            return false;
        }
        
        if (ext1.isEmpty() && ext2.isEmpty()) {
            return prefixOverlaps(
                p1.replaceAll("[*?]+$", ""),
                p2.replaceAll("[*?]+$", "")
            );
        }
        
        if (ext1.isEmpty() || ext2.isEmpty()) {
            if (p1.endsWith("*") && !p1.startsWith("*")) {
                String prefix1 = p1.substring(0, p1.length() - 1);
                if (p2.startsWith(prefix1) || prefix1.isEmpty() ||
                    (ext2.isEmpty() && p2.endsWith(prefix1))) return true;
            }
            
            if (p2.endsWith("*") && !p2.startsWith("*")) {
                String prefix2 = p2.substring(0, p2.length() - 1);
                if (p1.startsWith(prefix2) || prefix2.isEmpty() ||
                    (ext1.isEmpty() && p1.endsWith(prefix2))) return true;
            }
            
            if ((p1.startsWith("*.") && p2.endsWith("*")) ||
                (p2.startsWith("*.") && p1.endsWith("*"))) {
                return true;
            }
            
            return false;
        }
        
        if (p1.startsWith("*.") && p2.startsWith("*.")) {
            String prefix1 = p1.substring(2).replace(ext1, "");
            String prefix2 = p2.substring(2).replace(ext2, "");
            return prefixOverlaps(prefix1, prefix2);
        }
        
        if (p1.startsWith("*")) {
            String suffix = p1.substring(1).replace(ext1, "");
            if (suffix.isEmpty()) return true;
            if (p2.startsWith(suffix)) return true;
            if (ext1.isEmpty() && p2.endsWith(suffix)) return true;
            return false;
        }
        
        if (p2.startsWith("*")) {
            String suffix = p2.substring(1).replace(ext2, "");
            if (suffix.isEmpty()) return true;
            if (p1.startsWith(suffix)) return true;
            if (ext2.isEmpty() && p1.endsWith(suffix)) return true;
            return false;
        }
        
        if (p1.endsWith("*") && !p1.startsWith("*")) {
            String prefix = p1.substring(0, p1.length() - 1);
            if (p2.startsWith(prefix) || prefix.isEmpty()) return true;
        }
        
        if (p2.endsWith("*") && !p2.startsWith("*")) {
            String prefix = p2.substring(0, p2.length() - 1);
            if (p1.startsWith(prefix) || prefix.isEmpty()) return true;
        }
        
        return false;
    }
    
    private boolean subdirConflict(String p1, String p2) {
        String[] parts1 = p1.split("[/\\\\]");
        String[] parts2 = p2.split("[/\\\\]");
        
        String base1 = parts1[0];
        String base2 = parts2[0];
        
        if (base1.equals(base2)) {
            String rest1 = parts1.length > 1 ? parts1[parts1.length - 1] : "";
            String rest2 = parts2.length > 1 ? parts2[parts2.length - 1] : "";
            
            if (rest1.isEmpty() && rest2.isEmpty()) return true;
            if (rest1.isEmpty() || rest2.isEmpty()) return true;
            
            return flatConflict(rest1, rest2);
        }
        
        if (base1.equals("*") || base2.equals("*")) {
            return true;
        }
        
        if (isPrefixMatch(base1, base2) || isPrefixMatch(base2, base1)) {
            return true;
        }
        
        return false;
    }
    
    private boolean flatVsSubdirConflict(String flat, String subdir) {
        if (flat.equals("*")) return true;
        
        String[] subParts = subdir.split("[/\\\\]");
        String subFile = subParts[subParts.length - 1];
        
        if (flat.startsWith("*.")) {
            String ext = flat.substring(1);
            if (subFile.endsWith(ext) || ext.equals(".*") || ext.isEmpty()) {
                return true;
            }
            
            for (String part : subParts) {
                if (part.endsWith(ext)) return true;
            }
        }
        
        if (flatConflict(flat, subFile)) {
            return true;
        }
        
        if (flat.contains("*") && !flat.contains(".")) {
            String prefix = flat.replace("*", "");
            for (String part : subParts) {
                if (part.startsWith(prefix) || prefix.isEmpty()) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private boolean matchesWildcard(String text, String wildcard) {
        if (wildcard.equals("*")) return true;
        if (wildcard.equals(text)) return true;
        
        if (wildcard.startsWith("*.")) {
            String ext = wildcard.substring(1);
            if (ext.equals(".*") || ext.isEmpty()) return true;
            return text.endsWith(ext);
        }
        
        if (wildcard.endsWith("*")) {
            String prefix = wildcard.substring(0, wildcard.length() - 1);
            return text.startsWith(prefix);
        }
        
        if (wildcard.startsWith("*")) {
            String suffix = wildcard.substring(1);
            return text.contains(suffix) || text.endsWith(suffix);
        }
        
        return false;
    }
    
    private boolean isPrefixMatch(String text, String prefix) {
        return text.startsWith(prefix) || prefix.startsWith(text) || 
               text.equals("*") || prefix.equals("*");
    }
    
    private boolean prefixOverlaps(String prefix1, String prefix2) {
        if (prefix1.isEmpty() || prefix2.isEmpty()) return true;
        return prefix1.startsWith(prefix2) || prefix2.startsWith(prefix1);
    }
    
    private String getExtension(String pattern) {
        if (pattern == null || pattern.isEmpty()) return "";
        int lastDot = pattern.lastIndexOf('.');
        if (lastDot < 0) return "";
        return pattern.substring(lastDot);
    }
}
