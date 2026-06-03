package com.cq.agent.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Lightweight cron expression parser replacing Quartz CronScheduleBuilder.
 * Supports 5-field(min hour day month dow), 6-field(sec min hour day month dow), 7-field(sec min hour day month dow year).
 * Value formats: asterisk, question mark, specific value, range(1-5), step(0/5), list(1,3,5).
 */
public class CronExpression {

    private final String expression;

    private final Set<Integer> seconds = new TreeSet<>();
    private final Set<Integer> minutes = new TreeSet<>();
    private final Set<Integer> hours = new TreeSet<>();
    private final Set<Integer> daysOfMonth = new TreeSet<>();
    private final Set<Integer> months = new TreeSet<>();
    private final Set<Integer> daysOfWeek = new TreeSet<>();

    // 标记日和周字段是否为通配符（*或?），用于匹配逻辑
    private boolean domWildcard = true;
    private boolean dowWildcard = true;

    public CronExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Cron expression must not be empty");
        }
        this.expression = expression.trim();
        parse(this.expression);
    }

    private void parse(String expr) {
        String[] fields = expr.split("\\s+");
        if (fields.length < 5 || fields.length > 7) {
            throw new IllegalArgumentException(
                    "Cron expression must have 5-7 fields, got " + fields.length + ": " + expr);
        }

        int idx = 0;
        if (fields.length >= 6) {
            // 6字段 = 秒 分 时 日 月 周; 7字段 = 秒 分 时 日 月 周 年
            parseField(fields[idx++], seconds, 0, 59, "seconds");
        } else {
            // 5字段 = 分 时 日 月 周，秒默认0
            seconds.add(0);
        }
        parseField(fields[idx++], minutes, 0, 59, "minutes");
        parseField(fields[idx++], hours, 0, 23, "hours");

        // 日字段：?和*都视为通配符
        String domField = fields[idx++];
        domWildcard = isWildcard(domField);
        parseField(domField, daysOfMonth, 1, 31, "day-of-month");

        parseField(fields[idx++], months, 1, 12, "month");

        // 周字段：?和*都视为通配符
        String dowField = fields[idx++];
        dowWildcard = isWildcard(dowField);
        parseField(dowField, daysOfWeek, 0, 7, "day-of-week");

        // 规范化：周字段中7等价于0（都表示周日）
        if (daysOfWeek.contains(7)) {
            daysOfWeek.remove(7);
            daysOfWeek.add(0);
        }
    }

    private boolean isWildcard(String field) {
        return "*".equals(field) || "?".equals(field);
    }

    private void parseField(String field, Set<Integer> target, int min, int max, String fieldName) {
        if (isWildcard(field)) {
            for (int i = min; i <= max; i++) {
                target.add(i);
            }
            return;
        }

        for (String part : field.split(",")) {
            parsePart(part.trim(), target, min, max, fieldName);
        }
    }

    private void parsePart(String part, Set<Integer> target, int min, int max, String fieldName) {
        if (part.contains("/")) {
            String[] rangeAndStep = part.split("/", 2);
            int start;
            int step = Integer.parseInt(rangeAndStep[1].trim());
            if (step <= 0) {
                throw new IllegalArgumentException("Step must be positive in field " + fieldName + ": " + part);
            }
            if (isWildcard(rangeAndStep[0].trim())) {
                start = min;
            } else {
                start = Integer.parseInt(rangeAndStep[0].trim());
            }
            for (int i = start; i <= max; i += step) {
                if (i >= min) {
                    target.add(i);
                }
            }
        } else if (part.contains("-")) {
            String[] range = part.split("-", 2);
            int start = Integer.parseInt(range[0].trim());
            int end = Integer.parseInt(range[1].trim());
            for (int i = start; i <= end; i++) {
                if (i >= min && i <= max) {
                    target.add(i);
                }
            }
        } else {
            int value = Integer.parseInt(part.trim());
            if (value < min || value > max) {
                throw new IllegalArgumentException(
                        "Value " + value + " out of range [" + min + "-" + max + "] in field " + fieldName);
            }
            target.add(value);
        }
    }

    /**
     * 计算从给定时间之后的下一次执行时间
     */
    public LocalDateTime getNextExecutionTime(LocalDateTime afterTime) {
        if (afterTime == null) {
            throw new IllegalArgumentException("afterTime must not be null");
        }

        // 从afterTime+1秒开始搜索，确保返回的是"之后"的时间
        LocalDateTime candidate = afterTime.plusSeconds(1).withNano(0);

        // 最多搜索4年（覆盖闰年场景）
        LocalDateTime maxSearch = candidate.plusYears(4);

        while (!candidate.isAfter(maxSearch)) {
            // 逐级匹配：月 -> 日 -> 时 -> 分 -> 秒
            if (!months.contains(candidate.getMonthValue())) {
                candidate = jumpToNextMonth(candidate);
                continue;
            }
            if (!matchesDay(candidate)) {
                candidate = candidate.plusDays(1).withHour(0).withMinute(0).withSecond(0);
                continue;
            }
            if (!hours.contains(candidate.getHour())) {
                Integer nextHour = findNext(hours, candidate.getHour());
                if (nextHour != null) {
                    candidate = candidate.withHour(nextHour).withMinute(firstOf(minutes)).withSecond(firstOf(seconds));
                } else {
                    candidate = candidate.plusDays(1).withHour(firstOf(hours)).withMinute(firstOf(minutes))
                            .withSecond(firstOf(seconds));
                }
                continue;
            }
            if (!minutes.contains(candidate.getMinute())) {
                Integer nextMinute = findNext(minutes, candidate.getMinute());
                if (nextMinute != null) {
                    candidate = candidate.withMinute(nextMinute).withSecond(firstOf(seconds));
                } else {
                    Integer nextHour = findNext(hours, candidate.getHour());
                    if (nextHour != null) {
                        candidate = candidate.withHour(nextHour).withMinute(firstOf(minutes)).withSecond(firstOf(seconds));
                    } else {
                        candidate = candidate.plusDays(1).withHour(firstOf(hours)).withMinute(firstOf(minutes))
                                .withSecond(firstOf(seconds));
                    }
                }
                continue;
            }
            if (!seconds.contains(candidate.getSecond())) {
                Integer nextSecond = findNext(seconds, candidate.getSecond());
                if (nextSecond != null) {
                    candidate = candidate.withSecond(nextSecond);
                } else {
                    Integer nextMinute = findNext(minutes, candidate.getMinute());
                    if (nextMinute != null) {
                        candidate = candidate.withMinute(nextMinute).withSecond(firstOf(seconds));
                    } else {
                        Integer nextHour = findNext(hours, candidate.getHour());
                        if (nextHour != null) {
                            candidate = candidate.withHour(nextHour).withMinute(firstOf(minutes)).withSecond(firstOf(seconds));
                        } else {
                            candidate = candidate.plusDays(1).withHour(firstOf(hours)).withMinute(firstOf(minutes))
                                    .withSecond(firstOf(seconds));
                        }
                    }
                }
                continue;
            }

            // 所有字段都匹配
            return candidate;
        }

        throw new IllegalStateException("No valid execution time found within 4 years for: " + expression);
    }

    /**
     * 计算从给定时间之后的下一次执行时间（Date版本）
     */
    public Date getNextExecutionTime(Date afterTime) {
        LocalDateTime ldt = afterTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime next = getNextExecutionTime(ldt);
        return Date.from(next.atZone(ZoneId.systemDefault()).toInstant());
    }

    private boolean matchesDay(LocalDateTime time) {
        // Quartz行为：日和周都指定了非通配符值时，任一匹配即可
        // 其中一个是通配符时，只看另一个
        if (!domWildcard && !dowWildcard) {
            return daysOfMonth.contains(time.getDayOfMonth())
                    || daysOfWeek.contains(time.getDayOfWeek().getValue() % 7);
        } else if (!dowWildcard) {
            return daysOfWeek.contains(time.getDayOfWeek().getValue() % 7);
        } else {
            // domWildcard或两者都是通配符，只看日
            return daysOfMonth.contains(time.getDayOfMonth());
        }
    }

    private LocalDateTime jumpToNextMonth(LocalDateTime current) {
        Integer nextMonth = findNext(months, current.getMonthValue());
        if (nextMonth != null) {
            return current.withMonth(nextMonth).withDayOfMonth(1)
                    .withHour(firstOf(hours)).withMinute(firstOf(minutes)).withSecond(firstOf(seconds));
        } else {
            return current.plusYears(1).withMonth(firstOf(months)).withDayOfMonth(1)
                    .withHour(firstOf(hours)).withMinute(firstOf(minutes)).withSecond(firstOf(seconds));
        }
    }

    private Integer findNext(Set<Integer> set, int current) {
        for (int val : set) {
            if (val > current) {
                return val;
            }
        }
        return null;
    }

    private int firstOf(Set<Integer> set) {
        return set.iterator().next();
    }

    public String getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return "CronExpression[" + expression + "]";
    }
}
