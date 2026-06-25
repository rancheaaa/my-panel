package com.cq.agent.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class CronExpressionTest {

    // ==================== 解析测试 ====================

    @Test
    @DisplayName("6字段cron表达式解析成功(Quartz标准格式)")
    void parse6FieldCron() {
        assertDoesNotThrow(() -> new CronExpression("0 * * * * ?"));
        assertDoesNotThrow(() -> new CronExpression("0 0/5 * * * ?"));
        assertDoesNotThrow(() -> new CronExpression("30 0 1 ? * 1-5"));
    }

    @Test
    @DisplayName("7字段cron表达式解析成功(含年)")
    void parse7FieldCron() {
        assertDoesNotThrow(() -> new CronExpression("0 0 0 ? * * 2025"));
    }

    @Test
    @DisplayName("5字段cron表达式解析成功(传统Unix格式,秒默认0)")
    void parse5FieldCron() {
        assertDoesNotThrow(() -> new CronExpression("0 * * * *"));
        assertDoesNotThrow(() -> new CronExpression("*/5 * * * *"));
    }

    @Test
    @DisplayName("字段数不足抛异常")
    void parseTooFewFields() {
        assertThrows(IllegalArgumentException.class, () -> new CronExpression("0 * * *"));
        assertThrows(IllegalArgumentException.class, () -> new CronExpression(""));
        assertThrows(IllegalArgumentException.class, () -> new CronExpression("   "));
    }

    @Test
    @DisplayName("字段数过多抛异常")
    void parseTooManyFields() {
        assertThrows(IllegalArgumentException.class, () -> new CronExpression("0 0 0 0 0 0 0 0"));
    }

    // ==================== 下次执行时间计算(6字段Quartz格式) ====================

    @Test
    @DisplayName("每分钟执行(6字段) - 下次执行时间应为下一分钟0秒")
    void nextExecutionEveryMinute6Field() {
        CronExpression cron = new CronExpression("0 * * * * ?");
        LocalDateTime now = LocalDateTime.of(2025, 6, 3, 10, 30, 15);
        LocalDateTime next = cron.getNextExecutionTime(now);
        assertEquals(LocalDateTime.of(2025, 6, 3, 10, 31, 0), next);
    }

    @Test
    @DisplayName("每5分钟执行(6字段)")
    void nextExecutionEvery5Minutes6Field() {
        CronExpression cron = new CronExpression("0 0/5 * * * ?");
        LocalDateTime now = LocalDateTime.of(2025, 6, 3, 10, 32, 0);
        LocalDateTime next = cron.getNextExecutionTime(now);
        assertEquals(LocalDateTime.of(2025, 6, 3, 10, 35, 0), next);
    }

    @Test
    @DisplayName("每小时整点执行(6字段)")
    void nextExecutionEveryHour6Field() {
        CronExpression cron = new CronExpression("0 0 * * * ?");
        LocalDateTime now = LocalDateTime.of(2025, 6, 3, 10, 30, 0);
        LocalDateTime next = cron.getNextExecutionTime(now);
        assertEquals(LocalDateTime.of(2025, 6, 3, 11, 0, 0), next);
    }

    @Test
    @DisplayName("每天凌晨2点执行(6字段)")
    void nextExecutionDaily2AM6Field() {
        CronExpression cron = new CronExpression("0 0 2 * * ?");
        LocalDateTime now = LocalDateTime.of(2025, 6, 3, 10, 0, 0);
        LocalDateTime next = cron.getNextExecutionTime(now);
        assertEquals(LocalDateTime.of(2025, 6, 4, 2, 0, 0), next);
    }

    @Test
    @DisplayName("工作日每天9点30分执行(6字段,1-5表示周一到周五)")
    void nextExecutionWeekday930() {
        CronExpression cron = new CronExpression("0 30 9 ? * 1-5");
        // 周二 10:00 -> 下一个工作日9:30 应该是周三9:30
        LocalDateTime tuesday = LocalDateTime.of(2025, 6, 3, 10, 0, 0);
        LocalDateTime next = cron.getNextExecutionTime(tuesday);
        assertEquals(LocalDateTime.of(2025, 6, 4, 9, 30, 0), next);

        // 周一 8:00 -> 当天9:30
        LocalDateTime monday = LocalDateTime.of(2025, 6, 2, 8, 0, 0);
        next = cron.getNextExecutionTime(monday);
        assertEquals(LocalDateTime.of(2025, 6, 2, 9, 30, 0), next);

        // 周五 10:00 -> 下周一9:30
        LocalDateTime friday = LocalDateTime.of(2025, 6, 6, 10, 0, 0);
        next = cron.getNextExecutionTime(friday);
        assertEquals(LocalDateTime.of(2025, 6, 9, 9, 30, 0), next);
    }

    @Test
    @DisplayName("每月1号0点执行(6字段)")
    void nextExecutionMonthlyFirstDay() {
        CronExpression cron = new CronExpression("0 0 0 1 * ?");
        LocalDateTime now = LocalDateTime.of(2025, 6, 15, 0, 0, 0);
        LocalDateTime next = cron.getNextExecutionTime(now);
        assertEquals(LocalDateTime.of(2025, 7, 1, 0, 0, 0), next);
    }

    @Test
    @DisplayName("每30秒执行(6字段)")
    void nextExecutionEvery30Seconds() {
        CronExpression cron = new CronExpression("30 * * * * ?");
        LocalDateTime now = LocalDateTime.of(2025, 6, 3, 10, 0, 0);
        LocalDateTime next = cron.getNextExecutionTime(now);
        assertEquals(LocalDateTime.of(2025, 6, 3, 10, 0, 30), next);
    }

    // ==================== 5字段格式测试 ====================

    @Test
    @DisplayName("5字段格式 - 每小时第0分钟执行")
    void nextExecution5FieldHourly() {
        CronExpression cron = new CronExpression("0 * * * *");
        LocalDateTime now = LocalDateTime.of(2025, 6, 3, 10, 30, 0);
        LocalDateTime next = cron.getNextExecutionTime(now);
        assertEquals(LocalDateTime.of(2025, 6, 3, 11, 0, 0), next);
    }

    @Test
    @DisplayName("5字段格式 - 每5分钟执行")
    void nextExecution5FieldEvery5Min() {
        CronExpression cron = new CronExpression("*/5 * * * *");
        LocalDateTime now = LocalDateTime.of(2025, 6, 3, 10, 32, 0);
        LocalDateTime next = cron.getNextExecutionTime(now);
        assertEquals(LocalDateTime.of(2025, 6, 3, 10, 35, 0), next);
    }

    // ==================== 特殊值测试 ====================

    @Test
    @DisplayName("星号*匹配所有值")
    void asteriskMatchesAll() {
        CronExpression cron = new CronExpression("* * * * * ?");
        LocalDateTime now = LocalDateTime.of(2025, 6, 3, 10, 30, 0);
        LocalDateTime next = cron.getNextExecutionTime(now);
        assertNotNull(next);
        assertTrue(next.isAfter(now));
    }

    @Test
    @DisplayName("逗号分隔多个值")
    void commaSeparatedValues() {
        CronExpression cron = new CronExpression("0 0 0,12 * * ?");
        LocalDateTime am = LocalDateTime.of(2025, 6, 3, 5, 30, 0);
        LocalDateTime next = cron.getNextExecutionTime(am);
        assertEquals(LocalDateTime.of(2025, 6, 3, 12, 0, 0), next);

        LocalDateTime after12 = LocalDateTime.of(2025, 6, 3, 13, 0, 0);
        next = cron.getNextExecutionTime(after12);
        assertEquals(LocalDateTime.of(2025, 6, 4, 0, 0, 0), next);
    }

    @Test
    @DisplayName("步长值 0/10 表示每10单位")
    void stepValues() {
        CronExpression cron = new CronExpression("0 0/10 * * * ?");
        LocalDateTime now = LocalDateTime.of(2025, 6, 3, 10, 25, 0);
        LocalDateTime next = cron.getNextExecutionTime(now);
        assertEquals(LocalDateTime.of(2025, 6, 3, 10, 30, 0), next);
    }

    @Test
    @DisplayName("范围值 1-5(周字段)")
    void rangeValues() {
        CronExpression cron = new CronExpression("0 0 9 ? * 1-5");
        LocalDateTime saturday = LocalDateTime.of(2025, 6, 7, 10, 0, 0); // 周六
        LocalDateTime next = cron.getNextExecutionTime(saturday);
        assertEquals(LocalDateTime.of(2025, 6, 9, 9, 0, 0), next); // 下周一
    }

    @Test
    @DisplayName("问号? 在日和周字段等价于*")
    void questionMarkEqualsAsterisk() {
        CronExpression cron = new CronExpression("0 0 0 ? * *");
        LocalDateTime now = LocalDateTime.of(2025, 6, 3, 10, 0, 0);
        LocalDateTime next = cron.getNextExecutionTime(now);
        assertEquals(LocalDateTime.of(2025, 6, 4, 0, 0, 0), next);
    }

    // ==================== 边界条件 ====================

    @Test
    @DisplayName("当前时间刚好匹配时，返回下一个匹配时间")
    void currentExactMatchReturnsNext() {
        CronExpression cron = new CronExpression("0 0 * * * ?");
        LocalDateTime exact = LocalDateTime.of(2025, 6, 3, 10, 0, 0);
        LocalDateTime next = cron.getNextExecutionTime(exact);
        assertEquals(LocalDateTime.of(2025, 6, 3, 11, 0, 0), next);
    }

    @Test
    @DisplayName("跨月计算 - 1月31日之后2月没有31号")
    void crossMonthNo31st() {
        CronExpression cron = new CronExpression("0 0 0 31 * ?");
        LocalDateTime jan31 = LocalDateTime.of(2025, 1, 31, 1, 0, 0);
        LocalDateTime next = cron.getNextExecutionTime(jan31);
        assertEquals(LocalDateTime.of(2025, 3, 31, 0, 0, 0), next);
    }

    @Test
    @DisplayName("闰年2月29日")
    void leapYearFeb29() {
        CronExpression cron = new CronExpression("0 0 0 29 2 ?");
        LocalDateTime nonLeap = LocalDateTime.of(2025, 3, 1, 0, 0, 0);
        LocalDateTime next = cron.getNextExecutionTime(nonLeap);
        assertEquals(LocalDateTime.of(2028, 2, 29, 0, 0, 0), next);
    }

    @Test
    @DisplayName("getNextExecutionTime(Date) 重载方法正常工作")
    void nextExecutionFromDate() {
        CronExpression cron = new CronExpression("0 0 * * * ?");
        Date now = Date.from(LocalDateTime.of(2025, 6, 3, 10, 30, 0)
                .atZone(ZoneId.systemDefault()).toInstant());
        Date next = cron.getNextExecutionTime(now);
        assertNotNull(next);
        assertTrue(next.after(now));
    }

    @Test
    @DisplayName("连续多次计算下次执行时间应递增")
    void consecutiveNextExecutions() {
        CronExpression cron = new CronExpression("0 0/5 * * * ?");
        LocalDateTime t1 = LocalDateTime.of(2025, 6, 3, 10, 0, 0);
        LocalDateTime t2 = cron.getNextExecutionTime(t1);
        LocalDateTime t3 = cron.getNextExecutionTime(t2);
        LocalDateTime t4 = cron.getNextExecutionTime(t3);

        assertTrue(t2.isAfter(t1));
        assertTrue(t3.isAfter(t2));
        assertTrue(t4.isAfter(t3));

        assertEquals(LocalDateTime.of(2025, 6, 3, 10, 5, 0), t2);
        assertEquals(LocalDateTime.of(2025, 6, 3, 10, 10, 0), t3);
        assertEquals(LocalDateTime.of(2025, 6, 3, 10, 15, 0), t4);
    }

    // ==================== 异常场景 ====================

    @Test
    @DisplayName("无效cron字段值抛异常")
    void invalidFieldValue() {
        assertThrows(IllegalArgumentException.class, () -> new CronExpression("0 0 25 * * ?"));
        assertThrows(IllegalArgumentException.class, () -> new CronExpression("0 60 * * * ?"));
        assertThrows(IllegalArgumentException.class, () -> new CronExpression("0 0 0 32 * ?"));
        assertThrows(IllegalArgumentException.class, () -> new CronExpression("0 0 0 * 13 ?"));
    }

    @Test
    @DisplayName("无效步长值抛异常")
    void invalidStepValue() {
        assertThrows(IllegalArgumentException.class, () -> new CronExpression("0 0/0 * * * ?"));
    }

    @Test
    @DisplayName("null输入抛异常")
    void nullInput() {
        assertThrows(IllegalArgumentException.class, () -> new CronExpression(null));
        CronExpression cron = new CronExpression("0 * * * * ?");
        assertThrows(IllegalArgumentException.class, () -> cron.getNextExecutionTime((LocalDateTime) null));
    }
}
