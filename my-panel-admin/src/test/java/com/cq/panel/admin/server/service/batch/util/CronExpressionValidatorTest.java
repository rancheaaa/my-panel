package com.cq.panel.admin.server.service.batch.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cron表达式验证器 单元测试
 * 覆盖率目标：100%
 */
@DisplayName("Cron表达式验证器 - CronExpressionValidator")
class CronExpressionValidatorTest {

    private final CronExpressionValidator validator = new CronExpressionValidator();

    // ==================== 有效表达式测试 ====================
    
    @Nested
    @DisplayName("有效Cron表达式")
    class ValidExpressionsTests {
        
        @Test
        @DisplayName("1. 标准6位Cron表达式 → 验证通过")
        void testValidate_standard6Digit() {
            String cron = "0 */5 * * * ?";
            assertTrue(validator.isValid(cron), "标准6位Cron应有效");
            
            System.out.println("✅ 6位Cron有效: " + cron);
        }
        
        @Test
        @DisplayName("2. 带7位的Cron表达式（含年）→ 验证通过")
        void testValidate_7DigitWithYear() {
            String cron = "0 0 12 * * ? 2026";
            assertTrue(validator.isValid(cron), "7位Cron(含年)应有效");
            
            System.out.println("✅ 7位Cron有效: " + cron);
        }
        
        @Test
        @DisplayName("3. 基础Cron表达式 → 验证通过")
        void testValidate_basicExpressions() {
            String[] validCrons = {
                "0 */5 * * * ?",       // 每5分钟
                "0 0 12 * * ?",        // 每天中午12点
                "* * * * * *"           // 每秒执行（7位）
            };
            
            for (String cron : validCrons) {
                assertTrue(validator.isValid(cron), "基础Cron应有效: " + cron);
            }
            
            System.out.println("✅ 所有基础Cron表达式均有效");
        }
    }

    // ==================== 无效表达式测试 ====================
    
    @Nested
    @DisplayName("无效Cron表达式")
    class InvalidExpressionsTests {
        
        @Test
        @DisplayName("4. 空值或空白 → 验证失败")
        void testValidate_emptyOrNull() {
            assertFalse(validator.isValid(null), "null应无效");
            assertFalse(validator.isValid(""), "空字符串应无效");
            assertFalse(validator.isValid("   "), "纯空白应无效");
            
            System.out.println("✅ 空/Null正确拒绝");
        }
        
        @Test
        @DisplayName("5. 格式错误（位数不对）→ 验证失败")
        void testValidate_wrongFormat() {
            String[] invalidCrons = {
                "* * * *",           // 4位，太少
                "abc def ghi jkl"   // 非数字格式
            };
            
            for (String cron : invalidCrons) {
                assertFalse(validator.isValid(cron), "格式错误应无效: " + cron);
                
                String errorMsg = validator.getErrorMessage(cron);
                assertNotNull(errorMsg, "应有错误消息: " + cron);
                System.out.println("   ❌ " + cron + " → " + errorMsg);
            }
            
            System.out.println("✅ 格式错误正确拒绝");
        }
        
        @Test
        @DisplayName("6. 超出范围值 → 验证失败")
        void testValidate_outOfRange() {
            String[] outOfRangeCrons = {
                "70 * * * * *",      // 分钟=70（超出0-59）
                "0 60 * * * *",      // 小时=60（超出0-23）
                "0 0 32 * * *",     // 日=32（超出1-31）
                "0 0 0 13 * *",     // 月=13（超出1-12）
                "0 0 0 * 8 *"       // 周日=8（超出0-6或1-7）
            };
            
            for (String cron : outOfRangeCrons) {
                assertFalse(validator.isValid(cron), "超范围值应无效: " + cron);
                
                String errorMsg = validator.getErrorMessage(cron);
                assertNotNull(errorMsg);
                assertTrue(errorMsg.contains("范围") || errorMsg.contains("range") || 
                           errorMsg.contains("invalid"),
                           "错误消息应说明范围问题: " + errorMsg);
            }
            
            System.out.println("✅ 超范围值正确拒绝并给出友好提示");
        }
    }

    // ==================== 边界条件测试 ====================
    
    @Nested
    @DisplayName("边界条件处理")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("7. 友好错误消息")
        void testErrorMessage_friendly() {
            String invalidCron = "99 99 99 99 99 99";
            String error = validator.getErrorMessage(invalidCron);
            
            assertNotNull(error);
            assertTrue(error.length() > 10, "错误消息应详细");
            System.out.println("✅ 错误消息示例: " + error);
        }
        
        @Test
        @DisplayName("8. 友好错误消息")
        void testErrorMessage_friendly2() {
            String invalidCron = "99 99 99 99 99 99";
            String error = validator.getErrorMessage(invalidCron);
            
            assertNotNull(error);
            assertTrue(error.length() > 10, "错误消息应详细");
            System.out.println("✅ 错误消息示例: " + error);
        }
    }

    // ==================== 性能测试 ====================
    
    @Nested
    @DisplayName("性能验证")
    class PerformanceTests {
        
        @Test
        @DisplayName("9. 批量验证性能 < 300ms")
        void testPerformance_batchValidation() {
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < 10000; i++) {
                validator.isValid("0 */5 * * * ?");
            }
            
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("⚡ 10000次Cron验证耗时: " + duration + "ms");
            
            assertTrue(duration < 300, "10000次验证应在300ms内完成 (实际:" + duration + "ms)");
        }
    }
}
