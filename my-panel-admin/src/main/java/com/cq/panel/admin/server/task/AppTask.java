package com.cq.panel.admin.server.task;

import com.cq.panel.admin.server.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 定时任务调度测试
 * 
 * @author cq
 */
@Component("appTask")
public class AppTask
{
    private static final Logger log = LoggerFactory.getLogger(AppTask.class);

    private final JdbcTemplate jdbcTemplate;

    public AppTask(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 测试多参数方法
     * @param s 字符串参数
     * @param b 布尔参数true false
     * @param l long参数
     * @param d double参数
     * @param i 整型参数
     */
    public void runMultipleParams(String s, Boolean b, Long l, Double d, Integer i)
    {
        log.info(StringUtils.format("执行多参方法： 字符串类型{}，布尔类型{}，长整型{}，浮点型{}，整形{}", s, b, l, d, i));
    }

    /**
     * 测试有参方法
     * @param str 参数字符串
     */
    public void runSingleParam(String str)
    {
        log.info("执行有参方法：{}", str);
    }

    /**
     * 备份系统所有表快照至data目录下，生成一个sql文件
     */
    public void backupSystemAllTable()
    {
        String[] tables = {
            "sys_config", "sys_dept", "sys_dict_data", "sys_dict_type", "sys_job",
            "sys_menu", "sys_notice", "sys_post", "sys_role", "sys_role_dept",
            "sys_role_menu", "sys_user", "sys_user_post", "sys_user_role"
        };

        StringBuilder sb = new StringBuilder();
        for (String table : tables)
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + table);
            if (rows.isEmpty())
            {
                continue;
            }

            sb.append("INSERT IGNORE INTO `").append(table).append("` VALUES ");
            StringJoiner rowJoiner = new StringJoiner(",\n       ", "", ";\n\n");
            for (Map<String, Object> row : rows)
            {
                StringJoiner valJoiner = new StringJoiner(", ", "(", ")");
                for (Object val : row.values())
                {
                    if (val == null)
                    {
                        valJoiner.add("NULL");
                    }
                    else if (val instanceof byte[])
                    {
                        valJoiner.add("X'" + bytesToHex((byte[]) val) + "'");
                    }
                    else if (val instanceof String || val instanceof java.util.Date || val instanceof java.time.temporal.Temporal)
                    {
                        valJoiner.add("'" + val.toString().replace("'", "''") + "'");
                    }
                    else
                    {
                        valJoiner.add(val.toString());
                    }
                }
                rowJoiner.add(valJoiner.toString());
            }
            sb.append(rowJoiner);
        }

        try
        {
            File dataDir = new File("data");
            if (!dataDir.exists())
            {
                final boolean result = dataDir.mkdirs();
                log.info("创建data目录是否成功: {}", result);
            }
            File dataFile = new File(dataDir, "my-panel-admin-backup.sql");
            try (FileWriter writer = new FileWriter(dataFile))
            {
                writer.write(sb.toString());
            }
            log.info("数据库全量数据已导出到: {}", dataFile.getAbsolutePath());
        }
        catch (IOException e)
        {
            log.error("数据库全量数据导出失败", e);
        }
    }

    private String bytesToHex(byte[] bytes)
    {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes)
        {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
            {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
