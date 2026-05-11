package com.cq.panel.admin.server.quartz;

import com.cq.panel.admin.server.repository.domain.SysJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JobInvokeUtil 单元测试
 */
class JobInvokeUtilTest {

    @Test
    @DisplayName("测试Python脚本执行 - 简单打印")
    void testExecutePythonScript_SimplePrint() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("python测试");
        sysJob.setScriptType("python");
        sysJob.setScriptContent("print('Hello from Python')");

        try {
            String result = JobInvokeUtil.invokeMethod(sysJob);
            assertNotNull(result);
            System.out.println("Python输出: " + result);
            assertTrue(result.contains("Hello from Python"), "输出应包含 'Hello from Python'，实际: " + result);
        } catch (Exception e) {
            fail("Python脚本执行失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试Python脚本执行 - 数学计算")
    void testExecutePythonScript_MathCalculation() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("python数学计算");
        sysJob.setScriptType("python");
        sysJob.setScriptContent("print(2 + 3)");

        try {
            String result = JobInvokeUtil.invokeMethod(sysJob);
            assertNotNull(result);
            System.out.println("Python输出: " + result);
            assertTrue(result.contains("5"), "输出应包含 '5'，实际: " + result);
        } catch (Exception e) {
            fail("Python脚本执行失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试Python脚本执行 - 多行代码")
    void testExecutePythonScript_MultiLine() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("python多行测试");
        sysJob.setScriptType("python");
        sysJob.setScriptContent("x = 10\ny = 20\nprint(f'x={x}, y={y}, sum={x+y}')");

        try {
            String result = JobInvokeUtil.invokeMethod(sysJob);
            assertNotNull(result);
            System.out.println("Python输出: " + result);
            assertTrue(result.contains("x=10"), "输出应包含 'x=10'，实际: " + result);
            assertTrue(result.contains("y=20"), "输出应包含 'y=20'，实际: " + result);
            assertTrue(result.contains("sum=30"), "输出应包含 'sum=30'，实际: " + result);
        } catch (Exception e) {
            fail("Python脚本执行失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试Python脚本执行 - 脚本内容为空")
    void testExecutePythonScript_EmptyContent() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("空脚本测试");
        sysJob.setScriptType("python");
        sysJob.setScriptContent("");

        assertThrows(Exception.class, () -> {
            JobInvokeUtil.invokeMethod(sysJob);
        });
    }

    @Test
    @DisplayName("测试Python脚本执行 - 脚本名称为空")
    void testExecutePythonScript_EmptyName() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("");
        sysJob.setScriptType("python");
        sysJob.setScriptContent("print('test')");

        assertThrows(Exception.class, () -> {
            JobInvokeUtil.invokeMethod(sysJob);
        });
    }

    @Test
    @DisplayName("测试Python脚本执行 - 脚本类型为空")
    void testExecutePythonScript_EmptyType() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("测试");
        sysJob.setScriptType("");
        sysJob.setScriptContent("print('test')");

        assertThrows(Exception.class, () -> {
            JobInvokeUtil.invokeMethod(sysJob);
        });
    }

    @Test
    @DisplayName("测试不支持的脚本类型")
    void testExecuteScriptByType_UnsupportedType() {
        SysJob sysJob = new SysJob();
        sysJob.setJobType(3);
        sysJob.setScriptName("测试");
        sysJob.setScriptType("ruby");
        sysJob.setScriptContent("puts 'test'");

        assertThrows(Exception.class, () -> {
            JobInvokeUtil.invokeMethod(sysJob);
        });
    }
}
