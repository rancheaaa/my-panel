package com.cq.panel.admin.server.service.batch.util;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cron表达式验证器
 * 
 * 用于验证Quartz格式的Cron表达式（6位或7位），
 * 提供友好的错误消息和详细的验证结果。
 */
@Component
public class CronExpressionValidator {

    private static final int[] MAX_VALUES = {59, 23, 31, 12, 7};

    public boolean isValid(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return false;
        }

        String trimmed = cronExpression.trim();
        String[] parts = trimmed.split("\\s+");
        
        if (parts.length < 6 || parts.length > 7) {
            return false;
        }
        
        for (int i = 0; i < Math.min(5, parts.length); i++) {
            if (!isValidField(parts[i], i)) {
                return false;
            }
        }
        
        if (!isValidDayOfWeek(parts[4])) {
            return false;
        }
        
        if (parts.length == 7 && !isValidYear(parts[6])) {
            return false;
        }
        
        return true;
    }

    public String getErrorMessage(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return "Cron表达式不能为空";
        }

        String[] parts = cronExpression.trim().split("\\s+");
        
        if (parts.length < 6) {
            return "Cron表达式至少需要6位（秒 分 时 日 月 周），当前仅" + parts.length + "位";
        }
        
        if (parts.length > 7) {
            return "Cron表达式最多支持7位（含年份），当前" + parts.length + "位过多";
        }
        
        for (int i = 0; i < Math.min(5, parts.length); i++) {
            String error = validateFieldWithError(parts[i], i);
            if (error != null) {
                return error;
            }
        }
        
        String dayOfWeekError = validateDayOfWeekWithError(parts[4]);
        if (dayOfWeekError != null) {
            return dayOfWeekError;
        }
        
        if (parts.length == 7) {
            String yearError = validateYearWithError(parts[6]);
            if (yearError != null) {
                return yearError;
            }
        }
        
        return "未知的Cron表达式错误: " + cronExpression;
    }

    private boolean isValidField(String field, int fieldIndex) {
        if (field == null || field.trim().isEmpty()) {
            return false;
        }
        
        field = field.trim();
        
        if ("*".equals(field) || "?".equals(field)) {
            return true;
        }
        
        try {
            String[] values = field.split(",");
            
            for (String value : values) {
                value = value.trim();
                
                if ("*".equals(value) || "?".equals(value)) {
                    continue;
                }
                
                if (value.contains("/")) {
                    String[] rangeParts = value.split("/");
                    if (rangeParts.length != 2) return false;
                    String rangeOrValue = rangeParts[0].trim();
                    if (!"*".equals(rangeOrValue) && !"?".equals(rangeOrValue)) {
                        try {
                            int num = Integer.parseInt(rangeOrValue);
                            if (num < 0 || num > getMaxForField(fieldIndex)) return false;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                    try {
                        int increment = Integer.parseInt(rangeParts[1].trim());
                        if (increment <= 0) return false;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    continue;
                }
                
                if (value.contains("-")) {
                    String[] rangeParts = value.split("-");
                    if (rangeParts.length != 2) return false;
                    try {
                        int start = Integer.parseInt(rangeParts[0].trim());
                        int end = Integer.parseInt(rangeParts[1].trim());
                        if (start < 0 || start > getMaxForField(fieldIndex)) return false;
                        if (end < 0 || end > getMaxForField(fieldIndex)) return false;
                        if (start > end) return false;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    continue;
                }
                
                try {
                    int num = Integer.parseInt(value);
                    if (num < 0 || num > getMaxForField(fieldIndex)) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    if (!(value.equals("L") || value.equals("W") || value.contains("#"))) {
                        return false;
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidRangeOrValue(String value, int fieldIndex) {
        value = value.trim();
        
        if ("*".equals(value) || "?".equals(value)) {
            return true;
        }
        
        if (value.equals("L") || value.contains("W") || value.contains("#")) {
            return fieldIndex >= 2; // L/W/#只允许在日、周字段
        }
        
        try {
            int num = Integer.parseInt(value);
            return num >= 0 && num <= getMaxForField(fieldIndex);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidDayOfWeek(String field) {
        if ("?".equals(field) || "*".equals(field)) {
            return true;
        }
        
        if (field.contains("L") || field.contains("#")) {
            return true; // Quartz特殊语法
        }
        
        String[] values = field.split(",");
        for (String value : values) {
            value = value.trim();
            if ("?".equals(value) || "*".equals(value)) continue;
            
            try {
                int day = Integer.parseInt(value);
                if (day < 1 || day > 7) return false;
            } catch (NumberFormatException e) {
                if (!value.matches("[A-Z]{3}")) return false; // SUN/MON等
            }
        }
        
        return true;
    }

    private boolean isValidYear(String field) {
        if ("*".equals(field)) {
            return true;
        }
        
        try {
            int year = Integer.parseInt(field);
            return year >= 1970 && year <= 2099;
        } catch (NumberFormatException e) {
            String[] ranges = field.split(",");
            for (String range : ranges) {
                if (range.contains("-")) {
                    String[] parts = range.split("-");
                    if (parts.length != 2) return false;
                    int start = Integer.parseInt(parts[0].trim());
                    int end = Integer.parseInt(parts[1].trim());
                    if (start < 1970 || start > 2099) return false;
                    if (end < 1970 || end > 2099) return false;
                } else {
                    int year = Integer.parseInt(range.trim());
                    if (year < 1970 || year > 2099) return false;
                }
            }
            return true;
        }
    }

    private String validateFieldWithError(String field, int fieldIndex) {
        String[] fieldNames = {"秒", "分", "时", "日", "月"};
        
        try {
            String[] values = field.split(",");
            
            for (String value : values) {
                value = value.trim();
                
                if ("*".equals(value) || "?".equals(value)) {
                    continue;
                }
                
                if (value.contains("/")) {
                    String[] rangeParts = value.split("/");
                    if (rangeParts.length != 2) {
                        return "字段[" + fieldNames[fieldIndex] + "]的增量值格式错误: " + value;
                    }
                    
                    try {
                        int increment = Integer.parseInt(rangeParts[1].trim());
                        if (increment <= 0) {
                            return "字段[" + fieldNames[fieldIndex] + "]的增量值必须大于0: " + increment;
                        }
                        if (increment > getMaxForField(fieldIndex)) {
                            return "字段[" + fieldNames[fieldIndex] + "]的增量值超出范围(0-" + getMaxForField(fieldIndex) + "): " + increment;
                        }
                    } catch (NumberFormatException e) {
                        return "字段[" + fieldNames[fieldIndex] + "]的增量值不是有效数字: " + rangeParts[1];
                    }
                    continue;
                }
                
                if (value.contains("-")) {
                    String[] rangeParts = value.split("-");
                    if (rangeParts.length != 2) {
                        return "字段[" + fieldNames[fieldIndex] + "]的范围格式错误: " + value;
                    }
                    
                    try {
                        int start = Integer.parseInt(rangeParts[0].trim());
                        int end = Integer.parseInt(rangeParts[1].trim());
                        
                        if (start < 0 || start > getMaxForField(fieldIndex)) {
                            return "字段[" + fieldNames[fieldIndex] + "]的范围起始值超出边界(0-" + getMaxForField(fieldIndex) + "): " + start;
                        }
                        if (end < 0 || end > getMaxForField(fieldIndex)) {
                            return "字段[" + fieldNames[fieldIndex] + "]的范围结束值超出边界(0-" + getMaxForField(fieldIndex) + "): " + end;
                        }
                        if (start > end) {
                            return "字段[" + fieldNames[fieldIndex] + "]的起始值不能大于结束值: " + start + "-" + end;
                        }
                    } catch (NumberFormatException e) {
                        return "字段[" + fieldNames[fieldIndex] + "]包含非数字字符: " + value;
                    }
                    continue;
                }
                
                try {
                    int num = Integer.parseInt(value);
                    if (num < 0 || num > getMaxForField(fieldIndex)) {
                        return "字段[" + fieldNames[fieldIndex] + "]的值超出有效范围(0-" + getMaxForField(fieldIndex) + "): " + num;
                    }
                } catch (NumberFormatException e) {
                    if (!(value.equals("L") || value.contains("W"))) {
                        return "字段[" + fieldNames[fieldIndex] + "]包含无效字符: " + value;
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            return "字段[" + fieldNames[fieldIndex] + "]解析失败: " + e.getMessage();
        }
    }

    private String validateDayOfWeekWithError(String field) {
        if ("?".equals(field) || "*".equals(field)) {
            return null;
        }
        
        if (field.contains("L") || field.contains("#")) {
            return null; // 允许Quartz特殊语法
        }
        
        String[] values = field.split(",");
        for (String value : values) {
            value = value.trim();
            if ("?".equals(value) || "*".equals(value)) continue;
            
            try {
                int day = Integer.parseInt(value);
                if (day < 1 || day > 7) {
                    return "星期字段的有效范围是1-7(或SUN-SAT): " + day;
                }
            } catch (NumberFormatException e) {
                if (!value.matches("[A-Z]{3}")) {
                    return "星期字段包含无效值: " + value;
                }
            }
        }
        
        return null;
    }

    private String validateYearWithError(String field) {
        if ("*".equals(field)) {
            return null;
        }
        
        try {
            int year = Integer.parseInt(field);
            if (year < 1970 || year > 2099) {
                return "年份字段的有效范围是1970-2099: " + year;
            }
            return null;
        } catch (NumberFormatException e) {
            String[] ranges = field.split(",");
            for (String range : ranges) {
                if (range.contains("-")) {
                    String[] parts = range.split("-");
                    if (parts.length != 2) {
                        return "年份字段的范围格式错误: " + range;
                    }
                    try {
                        int start = Integer.parseInt(parts[0].trim());
                        int end = Integer.parseInt(parts[1].trim());
                        if (start < 1970 || start > 2099 || end < 1970 || end > 2099) {
                            return "年份范围必须在1970-2099之间: " + range;
                        }
                    } catch (NumberFormatException ex) {
                        return "年份包含非数字字符: " + range;
                    }
                } else {
                    try {
                        int year = Integer.parseInt(range.trim());
                        if (year < 1970 || year > 2099) {
                            return "年份必须在1970-2099之间: " + year;
                        }
                    } catch (NumberFormatException ex) {
                        return "年份包含无效字符: " + range;
                    }
                }
            }
            return null;
        }
    }

    private int getMaxForField(int fieldIndex) {
        if (fieldIndex < MAX_VALUES.length) {
            return MAX_VALUES[fieldIndex];
        }
        return Integer.MAX_VALUE;
    }
}
