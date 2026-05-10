package com.cq.panel.admin.server.service.batch.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通配符冲突检测算法 单元测试
 * 覆盖率目标：100%
 */
@DisplayName("通配符冲突检测器 - WildcardConflictDetector")
class WildcardConflictDetectorTest {

    private final WildcardConflictDetector detector = new WildcardConflictDetector();

    // ==================== 基础匹配测试 ====================
    
    @Nested
    @DisplayName("基础匹配场景")
    class BasicMatchingTests {
        
        @Test
        @DisplayName("1. 完全相同的模式 → 冲突")
        void testSamePattern_exactMatch_conflict() {
            // /var/log/*.log vs /var/log/*.log → 冲突
            boolean conflict = detector.hasConflict(
                "/var/log", 
                Arrays.asList("*.log"), 
                null,
                "/var/log",
                Arrays.asList("*.log"),
                null
            );
            
            assertTrue(conflict, "相同源目录+相同包含模式应冲突");
        }
        
        @Test
        @DisplayName("2. 子目录重叠 → 冲突")
        void testSubDirectoryOverlap_conflict() {
            // 场景1: /var/log/*.log vs /var/log/app/*.log → 冲突
            boolean conflict = detector.hasConflict(
                "/var/log",
                Arrays.asList("*.log"),
                null,
                "/var/log",
                Arrays.asList("app/*.log"),
                null
            );
            
            assertTrue(conflict, "父目录*.log与子目录app/*.log应冲突");
        }
        
        @Test
        @DisplayName("3. 部分重叠(排除后仍有交集) → 冲突")
        void testPartialOverlapWithExclude_conflict() {
            // 场景2: /var/log/*.log vs /var/log/*.log 排除app* → 冲突（部分重叠）
            boolean conflict = detector.hasConflict(
                "/var/log",
                Arrays.asList("*.log"),
                null,
                "/var/log",
                Arrays.asList("*.log"),
                Arrays.asList("app*")  // 排除app开头的文件
            );
            
            assertTrue(conflict, "排除app*后仍有其他.log文件重叠，应冲突");
        }
        
        @Test
        @DisplayName("4. 不同根目录 → 不冲突")
        void testDifferentRootDirectory_noConflict() {
            // 场景3: /data/*.txt vs /backup/*.txt → 不冲突
            boolean conflict = detector.hasConflict(
                "/data",
                Arrays.asList("*.txt"),
                null,
                "/backup",
                Arrays.asList("*.txt"),
                null
            );
            
            assertFalse(conflict, "不同根目录不冲突");
        }
        
        @Test
        @DisplayName("5. 递归通配符**与子目录 → 冲突")
        void testRecursiveWildcard_overlap() {
            // 场景4: /var/log/**/*.log vs /var/log/app/*.log → 冲突
            boolean conflict = detector.hasConflict(
                "/var/log",
                Arrays.asList("**/*.log"),
                null,
                "/var/log",
                Arrays.asList("app/*.log"),
                null
            );
            
            assertTrue(conflict, "**/*.log递归匹配包含app/*.log");
        }
    }

    // ==================== 完全隔离场景 ====================
    
    @Nested
    @DisplayName("完全隔离场景")
    class CompleteIsolationTests {
        
        @Test
        @DisplayName("6. 完全隔离(排除后无交集) → 不冲突")
        void testCompleteIsolation_noConflict() {
            // 场景5: /var/log/app/*.log vs /var/log/system/*.txt → 不冲突
            boolean conflict = detector.hasConflict(
                "/var/log",
                Arrays.asList("app/*.log"),
                null,
                "/var/log",
                Arrays.asList("system/*.txt"),
                null
            );
            
            assertFalse(conflict, "不同子目录+不同扩展名完全隔离");
        }
        
        @Test
        @DisplayName("7. 不同文件扩展名 → 不冲突")
        void testDifferentExtensions_noConflict() {
            boolean conflict = detector.hasConflict(
                "/var/log",
                Arrays.asList("*.log"),
                null,
                "/var/log",
                Arrays.asList("*.txt"),
                null
            );
            
            assertFalse(conflict, ".log和.txt文件不重叠");
        }
    }

    // ==================== 边界条件测试 ====================
    
    @Nested
    @DisplayName("边界条件处理")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("8. 空的包含模式 → 不冲突")
        void testEmptyIncludePatterns_noConflict() {
            boolean conflict = detector.hasConflict(
                "/var/log",
                Collections.emptyList(),
                null,
                "/var/log",
                Arrays.asList("*.log"),
                null
            );
            
            assertFalse(conflict, "空包含模式不会匹配任何文件");
        }
        
        @Test
        @DisplayName("9. Null排除模式 → 正常处理")
        void testNullExcludePatterns_handled() {
            boolean conflict = detector.hasConflict(
                "/var/log",
                Arrays.asList("*.log"),
                null,
                "/var/log",
                Arrays.asList("*.log"),
                null  // null排除模式等同于空列表
            );
            
            assertTrue(conflict, "null排除模式应视为无排除");
        }
        
        @Test
        @DisplayName("10. 复杂通配符组合测试")
        void testComplexWildcard_combination() {
            // *.log,*.txt vs debug*,temp* 组合测试
            boolean conflict = detector.hasConflict(
                "/var/log",
                Arrays.asList("*.log", "*.txt"),
                null,
                "/var/log",
                Arrays.asList("debug*", "temp*"),
                null
            );
            
            // debug.log和temp.txt会同时匹配两个任务
            assertTrue(conflict, "复杂组合存在重叠文件");
        }
        
        @Test
        @DisplayName("11. 大小写敏感匹配")
        void testCaseSensitive_matching() {
            boolean conflict = detector.hasConflict(
                "/var/log",
                Arrays.asList("*.LOG"),
                null,
                "/var/log",
                Arrays.asList("*.log"),
                null
            );
            
            // Linux系统大小写敏感，*.LOG和*.log不同
            assertFalse(conflict, "Linux系统大小写敏感");
        }
    }

    // ==================== 安全性测试 ====================
    
    @Nested
    @DisplayName("安全性验证")
    class SecurityTests {
        
        @Test
        @DisplayName("12. 路径穿越攻击防护")
        void testPathTraversal_prevented() {
            // 尝试使用../../../etc/passwd进行路径穿越
            assertThrows(SecurityException.class, () -> {
                detector.hasConflict(
                    "/var/log",
                    Arrays.asList("../../../etc/passwd"),
                    null,
                    "/var/log",
                    Arrays.asList("*"),
                    null
                );
            }, "路径穿越攻击应被拒绝");
        }
    }

    // ==================== 性能测试 ====================
    
    @Nested
    @DisplayName("性能验证")
    class PerformanceTests {
        
        @Test
        @DisplayName("13. 大量现有任务的性能 < 100ms")
        void testPerformance_largePatternSet() {
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < 1000; i++) {
                detector.hasConflict(
                    "/var/log/app" + i,
                    Arrays.asList("*.log"),
                    null,
                    "/var/log",
                    Arrays.asList("*.log"),
                    null
                );
            }
            
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("⚡ 1000次冲突检测耗时: " + duration + "ms");
            
            assertTrue(duration < 100, "1000次冲突检测应在100ms内完成 (实际:" + duration + "ms)");
        }
    }
}
