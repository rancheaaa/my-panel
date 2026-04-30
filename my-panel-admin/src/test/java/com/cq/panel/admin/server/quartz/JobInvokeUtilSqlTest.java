package com.cq.panel.admin.server.quartz;

import com.cq.panel.admin.server.repository.domain.SysJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JobInvokeUtilSqlTest {

    @Test
    @Order(1)
    @DisplayName("测试SQL脚本执行 - 简单查询")
    void testExecuteSqlScript_SimpleSelect() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("简单查询测试");
        sysJob.setScriptType("sql");
        sysJob.setScriptContent("SELECT 1 AS test_column");

        try {
            String result = JobInvokeUtil.invokeMethod(sysJob);
            assertNotNull(result);
            System.out.println("SQL查询结果: " + result);
            assertTrue(result.contains("SQL脚本执行成功"), "应返回成功消息");
            assertTrue(result.contains("1"), "应包含查询结果");
        } catch (Exception e) {
            fail("SQL脚本执行失败: " + e.getMessage());
        }
    }

    @Test
    @Order(2)
    @DisplayName("测试SQL脚本执行 - 创建表")
    void testExecuteSqlScript_CreateTable() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("创建表测试");
        sysJob.setScriptType("sql");
        sysJob.setScriptContent(
            "CREATE TABLE IF NOT EXISTS test_job_sql (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "name VARCHAR(100), " +
            "age INT, " +
            "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
        );

        try {
            String result = JobInvokeUtil.invokeMethod(sysJob);
            assertNotNull(result);
            System.out.println("创建表结果: " + result);
            assertTrue(result.contains("SQL脚本执行成功"), "应返回成功消息");
        } catch (Exception e) {
            fail("SQL脚本执行失败: " + e.getMessage());
        }
    }

    @Test
    @Order(3)
    @DisplayName("测试SQL脚本执行 - 插入数据")
    void testExecuteSqlScript_InsertData() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("插入数据测试");
        sysJob.setScriptType("sql");
        sysJob.setScriptContent(
            "INSERT INTO test_job_sql (name, age) VALUES ('张三', 25)"
        );

        try {
            String result = JobInvokeUtil.invokeMethod(sysJob);
            assertNotNull(result);
            System.out.println("插入数据结果: " + result);
            assertTrue(result.contains("SQL脚本执行成功"), "应返回成功消息");
            assertTrue(result.contains("影响 1 行"), "应显示影响行数");
        } catch (Exception e) {
            fail("SQL脚本执行失败: " + e.getMessage());
        }
    }

    @Test
    @Order(4)
    @DisplayName("测试SQL脚本执行 - 多条SQL语句")
    void testExecuteSqlScript_MultipleStatements() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("多条SQL测试");
        sysJob.setScriptType("sql");
        sysJob.setScriptContent(
            "INSERT INTO test_job_sql (name, age) VALUES ('李四', 30);" +
            "INSERT INTO test_job_sql (name, age) VALUES ('王五', 35);" +
            "INSERT INTO test_job_sql (name, age) VALUES ('赵六', 40)"
        );

        try {
            String result = JobInvokeUtil.invokeMethod(sysJob);
            assertNotNull(result);
            System.out.println("多条SQL执行结果: " + result);
            assertTrue(result.contains("SQL脚本执行成功"), "应返回成功消息");
            assertTrue(result.contains("共执行 3 条SQL"), "应显示执行了3条SQL");
        } catch (Exception e) {
            fail("SQL脚本执行失败: " + e.getMessage());
        }
    }

    @Test
    @Order(5)
    @DisplayName("测试SQL脚本执行 - 查询数据")
    void testExecuteSqlScript_SelectData() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("查询数据测试");
        sysJob.setScriptType("sql");
        sysJob.setScriptContent("SELECT id, name, age FROM test_job_sql ORDER BY id");

        try {
            String result = JobInvokeUtil.invokeMethod(sysJob);
            assertNotNull(result);
            System.out.println("查询数据结果: " + result);
            assertTrue(result.contains("SQL脚本执行成功"), "应返回成功消息");
            assertTrue(result.contains("查询成功"), "应显示查询成功");
        } catch (Exception e) {
            fail("SQL脚本执行失败: " + e.getMessage());
        }
    }

    @Test
    @Order(6)
    @DisplayName("测试SQL脚本执行 - 更新数据")
    void testExecuteSqlScript_UpdateData() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("更新数据测试");
        sysJob.setScriptType("sql");
        sysJob.setScriptContent(
            "UPDATE test_job_sql SET age = 26 WHERE name = '张三'"
        );

        try {
            String result = JobInvokeUtil.invokeMethod(sysJob);
            assertNotNull(result);
            System.out.println("更新数据结果: " + result);
            assertTrue(result.contains("SQL脚本执行成功"), "应返回成功消息");
        } catch (Exception e) {
            fail("SQL脚本执行失败: " + e.getMessage());
        }
    }

    @Test
    @Order(7)
    @DisplayName("测试SQL脚本执行 - 删除数据")
    void testExecuteSqlScript_DeleteData() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("删除数据测试");
        sysJob.setScriptType("sql");
        sysJob.setScriptContent(
            "DELETE FROM test_job_sql WHERE name = '赵六'"
        );

        try {
            String result = JobInvokeUtil.invokeMethod(sysJob);
            assertNotNull(result);
            System.out.println("删除数据结果: " + result);
            assertTrue(result.contains("SQL脚本执行成功"), "应返回成功消息");
        } catch (Exception e) {
            fail("SQL脚本执行失败: " + e.getMessage());
        }
    }

    @Test
    @Order(8)
    @DisplayName("测试SQL脚本执行 - 混合SQL语句（查询+更新）")
    void testExecuteSqlScript_MixedStatements() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("混合SQL测试");
        sysJob.setScriptType("sql");
        sysJob.setScriptContent(
            "SELECT COUNT(*) AS total FROM test_job_sql;" +
            "UPDATE test_job_sql SET age = age + 1 WHERE age < 40;" +
            "SELECT id, name, age FROM test_job_sql ORDER BY id"
        );

        try {
            String result = JobInvokeUtil.invokeMethod(sysJob);
            assertNotNull(result);
            System.out.println("混合SQL执行结果: " + result);
            assertTrue(result.contains("SQL脚本执行成功"), "应返回成功消息");
            assertTrue(result.contains("共执行 3 条SQL"), "应显示执行了3条SQL");
        } catch (Exception e) {
            fail("SQL脚本执行失败: " + e.getMessage());
        }
    }

    @Test
    @Order(9)
    @DisplayName("测试SQL脚本执行 - 错误SQL回滚")
    void testExecuteSqlScript_ErrorRollback() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("错误回滚测试");
        sysJob.setScriptType("sql");
        sysJob.setScriptContent(
            "INSERT INTO test_job_sql (name, age) VALUES ('测试回滚', 99);" +
            "INSERT INTO non_existent_table (id) VALUES (1);" +
            "INSERT INTO test_job_sql (name, age) VALUES ('不应该插入', 99)"
        );

        try {
            String result = JobInvokeUtil.invokeMethod(sysJob);
            fail("应该抛出异常，因为包含错误SQL");
        } catch (Exception e) {
            System.out.println("错误回滚测试结果: " + e.getMessage());
            assertTrue(e.getMessage().contains("失败"), "应包含失败信息");
            assertTrue(e.getMessage().contains("回滚"), "应包含回滚信息");
        }
    }

    @Test
    @Order(10)
    @DisplayName("测试SQL脚本执行 - SQL内容为空")
    void testExecuteSqlScript_EmptyContent() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("空SQL测试");
        sysJob.setScriptType("sql");
        sysJob.setScriptContent("");

        assertThrows(Exception.class, () -> {
            JobInvokeUtil.invokeMethod(sysJob);
        }, "空SQL脚本应该抛出异常");
    }

    @Test
    @Order(11)
    @DisplayName("测试SQL脚本执行 - 脚本名称为空")
    void testExecuteSqlScript_EmptyName() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("");
        sysJob.setScriptType("sql");
        sysJob.setScriptContent("SELECT 1");

        assertThrows(Exception.class, () -> {
            JobInvokeUtil.invokeMethod(sysJob);
        }, "脚本名称为空应该抛出异常");
    }

    @Test
    @Order(12)
    @DisplayName("测试SQL脚本执行 - 清理测试数据")
    void testExecuteSqlScript_CleanupTestData() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("清理测试数据");
        sysJob.setScriptType("sql");
        sysJob.setScriptContent("DROP TABLE IF EXISTS test_job_sql");

        try {
            String result = JobInvokeUtil.invokeMethod(sysJob);
            assertNotNull(result);
            System.out.println("清理测试数据结果: " + result);
            assertTrue(result.contains("SQL脚本执行成功"), "应返回成功消息");
        } catch (Exception e) {
            fail("SQL脚本执行失败: " + e.getMessage());
        }
    }
}
