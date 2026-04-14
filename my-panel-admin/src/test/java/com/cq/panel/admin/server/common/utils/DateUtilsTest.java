package com.cq.panel.admin.server.common.utils;

import java.text.ParseException;
import java.util.Date;

public class DateUtilsTest
{
    public static void main(String[] args)
    {
        System.out.println("=== 日期解析测试 ===");
        
        String[] testDates = {
            "2024-01-01",
            "2024-01-01 12:00:00",
            "2024-01-01 12:00",
            "2024/01/01",
            "2024/01/01 12:00:00",
            "2024.01.01",
            "2024.01.01 12:00:00"
        };
        
        for (String dateStr : testDates)
        {
            Date date = DateUtils.parseDate(dateStr);
            System.out.println("原始字符串: " + dateStr + " -> 解析结果: " + 
                (date != null ? DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, date) : "解析失败"));
        }
        
        System.out.println("\n=== 错误处理测试 ===");
        String invalidDate = "invalid-date";
        Date invalidResult = DateUtils.parseDate(invalidDate);
        System.out.println("无效日期: " + invalidDate + " -> 解析结果: " + 
            (invalidResult != null ? DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, invalidResult) : "null (正确处理)"));
        
        System.out.println("\n=== 边界测试 ===");
        String emptyDate = "";
        Date emptyResult = DateUtils.parseDate(emptyDate);
        System.out.println("空字符串: '" + emptyDate + "' -> 解析结果: " + 
            (emptyResult != null ? DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, emptyResult) : "null (正确处理)"));
        
        String nullDate = null;
        Date nullResult = DateUtils.parseDate(nullDate);
        System.out.println("null值: " + nullDate + " -> 解析结果: " + 
            (nullResult != null ? DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, nullResult) : "null (正确处理)"));
    }
}