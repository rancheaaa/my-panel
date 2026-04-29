package com.cq.panel.admin.server.quartz;

import com.cq.panel.admin.server.common.utils.MyStringUtils;
import com.cq.panel.admin.server.common.utils.spring.SpringUtils;
import com.cq.panel.admin.server.repository.domain.SysJob;
import com.cq.panel.common.loadbalancer.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

/**
 * 任务执行工具
 *
 * @author cq
 */
@Slf4j
public class JobInvokeUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final HealthChecker HEALTH_CHECKER = HealthCheckerFactory.createDefault();

    private static final java.util.concurrent.ConcurrentHashMap<String, MethodHandle> METHOD_HANDLE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 执行方法
     *
     * @param sysJob 系统任务
     */
    public static String invokeMethod(SysJob sysJob) throws Exception {
        Integer jobType = sysJob.getJobType();
        if (jobType == 1) {
            invokeInternalMethod(sysJob);
            return "success";
        } else if (jobType == 2) {
            return invokeHttpInterface(sysJob);
        } else if (jobType == 3) {
            return invokeScript(sysJob);
        } else {
            throw new IllegalArgumentException("not support job type "  + jobType);
        }
    }

    /**
     * 1-内置方法调度
     */
    private static void invokeInternalMethod(SysJob sysJob) throws Exception {
        String methodNameFull = sysJob.getMethodName();
        if (MyStringUtils.isEmpty(methodNameFull)) {
            throw new Exception("内置方法全限定名不能为空");
        }

        if (methodNameFull.contains("(") && methodNameFull.contains(")")) {
            SysJob legacy = new SysJob();
            legacy.setInvokeTarget(methodNameFull);
            invokeTargetMethod(legacy);
            return;
        }

        int lastDotIndex = methodNameFull.lastIndexOf(".");
        if (lastDotIndex == -1) {
            throw new Exception("内置方法格式错误，需为：com.xxx.ClassName.methodName");
        }

        String className = methodNameFull.substring(0, lastDotIndex);
        String methodName = methodNameFull.substring(lastDotIndex + 1);

        Object bean;
        try {
            String beanName = MyStringUtils.uncapitalize(className.substring(className.lastIndexOf(".") + 1));
            bean = SpringUtils.getBean(beanName);
        } catch (Exception e) {
            try {
                bean = SpringUtils.getBean(Class.forName(className));
            } catch (Exception e2) {
                bean = Class.forName(className).getDeclaredConstructor().newInstance();
            }
        }

        String cacheKey = className + "#" + methodName + "#" + bean.getClass().getName();
        final Object finalBean = bean;
        MethodHandle methodHandle = METHOD_HANDLE_CACHE.computeIfAbsent(cacheKey, key -> {
            try {
                Method method = finalBean.getClass().getMethod(methodName);
                return MethodHandles.lookup().unreflect(method);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("获取方法句柄失败: " + className + "." + methodName, e);
            }
        });

        try {
            methodHandle.invoke(bean);
        } catch (Throwable e) {
            if (e instanceof Exception) {
                throw (Exception) e;
            }
            throw new Exception("方法调用失败: " + className + "." + methodName, e);
        }
    }

    private static void invokeTargetMethod(SysJob sysJob) throws Exception {
        String invokeTarget = sysJob.getInvokeTarget();
        String beanName = getBeanName(invokeTarget);
        String methodName = getMethodName(invokeTarget);
        List<Object[]> methodParams = getMethodParams(invokeTarget);

        Object bean;
        if (!isValidClassName(beanName)) {
            bean = SpringUtils.getBean(beanName);
        } else {
            bean = Class.forName(beanName).getDeclaredConstructor().newInstance();
        }

        if (MyStringUtils.isNotNull(methodParams) && !methodParams.isEmpty()) {
            Class<?>[] paramTypes = getMethodParamsType(methodParams);
            Method method = findCompatibleMethod(bean.getClass(), methodName, paramTypes);
            Object[] paramValues = convertParams(methodParams, method.getParameterTypes());

            String cacheKey = bean.getClass().getName() + "#" + methodName + "#" + paramTypes.length;
            MethodHandle methodHandle = METHOD_HANDLE_CACHE.computeIfAbsent(cacheKey, key -> {
                try {
                    return MethodHandles.lookup().unreflect(method);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("获取方法句柄失败: " + bean.getClass().getName() + "." + methodName, e);
                }
            });

            try {
                methodHandle.invokeWithArguments(buildArgumentsList(bean, paramValues));
            } catch (Throwable e) {
                if (e instanceof Exception) {
                    throw (Exception) e;
                }
                throw new Exception("带参数方法调用失败: " + bean.getClass().getName() + "." + methodName, e);
            }
        } else {
            String cacheKey = bean.getClass().getName() + "#" + methodName + "#0";
            MethodHandle methodHandle = METHOD_HANDLE_CACHE.computeIfAbsent(cacheKey, key -> {
                try {
                    Method method = bean.getClass().getMethod(methodName);
                    return MethodHandles.lookup().unreflect(method);
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new RuntimeException("获取方法句柄失败: " + bean.getClass().getName() + "." + methodName, e);
                }
            });

            try {
                methodHandle.invoke(bean);
            } catch (Throwable e) {
                if (e instanceof Exception) {
                    throw (Exception) e;
                }
                throw new Exception("方法调用失败: " + bean.getClass().getName() + "." + methodName, e);
            }
        }
    }

    private static java.util.ArrayList<Object> buildArgumentsList(Object bean, Object[] paramValues) {
        java.util.ArrayList<Object> args = new java.util.ArrayList<>(paramValues.length + 1);
        args.add(bean);
        args.addAll(Arrays.asList(paramValues));
        return args;
    }

    /**
     * 2-HTTP 接口调度
     */
    private static String invokeHttpInterface(SysJob sysJob) throws Exception {
        RestTemplate restTemplate = SpringUtils.getBean(RestTemplate.class);
        String httpUrl = sysJob.getHttpUrl();
        String methodStr = sysJob.getHttpMethod();
        String headersJson = sysJob.getHttpHeaders();
        String body = replacePlaceholders(sysJob.getHttpBody(), sysJob);
        String loadBalanceStrategy = sysJob.getLoadBalanceStrategy();

        HttpMethod httpMethod = HttpMethod.valueOf(methodStr.toUpperCase());
        HttpHeaders headers = new HttpHeaders();

        if (MyStringUtils.isNotEmpty(headersJson)) {
            Map<String, String> headerMap = objectMapper.readValue(headersJson, new TypeReference<>() {
            });
            headerMap.forEach(headers::add);
        }

        if (headers.getContentType() == null && MyStringUtils.isNotEmpty(body)) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        String selectedUrl;
        if (MyStringUtils.isNotEmpty(httpUrl) && httpUrl.contains(",")) {
            String[] urls = httpUrl.split(",");
            LoadBalancerAlgorithm algorithm = LoadBalancerAlgorithm.fromName(loadBalanceStrategy);
            LoadBalancer loadBalancer = LoadBalancerFactory.createLoadBalancer(algorithm);
            selectedUrl = choose(urls, loadBalancer);
            log.info("使用负载均衡策略：{}，从{}个URL中选择：{}", algorithm.getName(), urls.length, selectedUrl);
        } else {
            selectedUrl = replacePlaceholders(httpUrl, sysJob);
        }

        ResponseEntity<String> response = restTemplate.exchange(selectedUrl, httpMethod, entity, String.class);
        log.info("HTTP接口调用完成，sysJobId：{}，jobName：{}，jobGroup：{}，invokeTarget：{}，URL：{}，状态码：{}，响应内容：{}",
                sysJob.getJobId(), sysJob.getJobName(), sysJob.getJobGroup(), sysJob.getInvokeTarget(), selectedUrl,
                response.getStatusCode().value(), response.getBody());
        if (response.getStatusCode().isError()) {
            log.error("HTTP接口调用失败，sysJobId：{}，jobName：{}，jobGroup：{}，invokeTarget：{}，URL：{}，状态码：{}，响应内容：{}",
                    sysJob.getJobId(), sysJob.getJobName(), sysJob.getJobGroup(), sysJob.getInvokeTarget(), selectedUrl,
                    response.getStatusCode().value(), response.getBody());
            throw new Exception("HTTP 接口调用失败，sysJobId：{}，jobName：{}，jobGroup：{}，invokeTarget：{}，URL：{}，状态码："
                    + response.getStatusCode().value() + "，响应内容：" + response.getBody());
        }
        return selectedUrl;
    }

    /**
     * 从URL数组中选择一个URL（用于定时任务负载均衡）
     *
     * @param urls URL数组
     * @return 选中的URL
     */
    private static String choose(String[] urls, LoadBalancer loadBalancer) {
        try {
            if (urls == null || urls.length == 0) {
                throw new IllegalArgumentException("URL array cannot be null or empty");
            }

            // 创建临时服务器列表
            List<Server> servers = new ArrayList<>();
            for (String url : urls) {
                if (url != null && !url.trim().isEmpty()) {
                    String trimmedUrl = url.trim();
                    // 解析URL并创建Server对象
                    try {
                        String scheme = "http";
                        String host;
                        int port;

                        // 提取协议
                        if (trimmedUrl.contains("://")) {
                            scheme = trimmedUrl.substring(0, trimmedUrl.indexOf("://"));
                            trimmedUrl = trimmedUrl.substring(trimmedUrl.indexOf("://") + 3);
                        }

                        // 提取主机和端口
                        String[] parts = trimmedUrl.split("/");
                        String hostPort = parts[0];

                        if (hostPort.contains(":")) {
                            String[] hostPortParts = hostPort.split(":");
                            host = hostPortParts[0];
                            port = Integer.parseInt(hostPortParts[1]);
                        } else {
                            host = hostPort;
                            port = "https".equals(scheme) ? 443 : 80;
                        }

                        servers.add(new Server(host, port));
                    } catch (Exception e) {
                        log.warn("Failed to parse URL: {}, skipping", url, e);
                    }
                }
            }

            if (servers.isEmpty()) {
                throw new IllegalArgumentException("No valid URLs provided");
            }

            for (int i = 0; i < servers.size(); i++) {
                // 执行负载均衡选择
                Server server = loadBalancer.choose(servers);
                if (server != null && JobInvokeUtil.HEALTH_CHECKER.isHealthy(server)) {
                    return server.getUrl();
                }
            }
            throw new IllegalStateException("No URL available for load balancing");
        } catch (Exception e) {
            log.error("Load balancing failed", e);
            throw new RuntimeException("Failed to choose URL for load balancing", e);
        }
    }

    /**
     * 3-脚本调度
     */
    private static String invokeScript(SysJob sysJob) throws Exception {
        String scriptName = sysJob.getScriptName();
        String scriptType = sysJob.getScriptType();
        String scriptContent = sysJob.getScriptContent();

        if (MyStringUtils.isEmpty(scriptName)) {
            throw new Exception("脚本名称不能为空");
        }
        if (MyStringUtils.isEmpty(scriptType)) {
            throw new Exception("脚本类型不能为空");
        }
        if (MyStringUtils.isEmpty(scriptContent)) {
            throw new Exception("脚本内容不能为空");
        }

        log.info("开始执行脚本，sysJobId：{}，jobName：{}，jobGroup：{}，脚本名称：{}，脚本类型：{}",
                sysJob.getJobId(), sysJob.getJobName(), sysJob.getJobGroup(), scriptName, scriptType);

        String output = executeScriptByType(scriptType, scriptContent);
        log.info("脚本执行成功，sysJobId：{}，jobName：{}，jobGroup：{}，脚本名称：{}，脚本类型：{}，执行输出：{}",
                sysJob.getJobId(), sysJob.getJobName(), sysJob.getJobGroup(), scriptName, scriptType, output);
        return output;
    }

    /**
     * 根据脚本类型执行脚本
     */
    static String executeScriptByType(String scriptType, String scriptContent) throws Exception {
        return switch (scriptType.toLowerCase()) {
            case "python" -> executePythonScript(scriptContent);
            case "shell" -> executeShellScript(scriptContent);
            case "cmd" -> executeCmdScript(scriptContent);
            case "powershell" -> executePowerShellScript(scriptContent);
            case "sql" -> executeSqlScript(scriptContent);
            default -> throw new Exception("不支持的脚本类型：" + scriptType);
        };
    }

    /**
     * 执行Python脚本
     */
    private static String executePythonScript(String scriptContent) throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");
        boolean hasGuiCode = containsGuiCode(scriptContent);
        
        String pythonCommand = isWindows ? "python" : "python3";
        String tempFile = createTempScriptFile(scriptContent, ".py");
        
        log.info("操作系统: {}, Python命令: {}, GUI代码: {}, 临时文件: {}", 
                os, pythonCommand, hasGuiCode, tempFile);

        ProcessBuilder processBuilder;
        if (isWindows && hasGuiCode) {
            processBuilder = buildWindowsGuiProcess(pythonCommand, tempFile);
        } else {
            processBuilder = new ProcessBuilder(pythonCommand, tempFile);
        }
        processBuilder.redirectErrorStream(true);
        
        try {
            return executeProcess(processBuilder);
        } catch (Exception e) {
            log.error("Python脚本执行失败，尝试使用python3命令", e);
            if (isWindows) {
                ProcessBuilder processBuilder2;
                if (hasGuiCode) {
                    processBuilder2 = buildWindowsGuiProcess("python3", tempFile);
                } else {
                    processBuilder2 = new ProcessBuilder("python3", tempFile);
                }
                processBuilder2.redirectErrorStream(true);
                return executeProcess(processBuilder2);
            }
            throw e;
        }
    }

    /**
     * 构建Windows GUI进程（使用PowerShell Start-Process）
     */
    private static ProcessBuilder buildWindowsGuiProcess(String pythonCommand, String scriptFile) {
        String psCommand = String.format(
                "Start-Process -FilePath '%s' -ArgumentList '%s' -WindowStyle Normal -Wait",
                pythonCommand, scriptFile);
        log.info("使用PowerShell Start-Process启动GUI脚本");
        return new ProcessBuilder("powershell", "-Command", psCommand);
    }

    /**
     * 检测脚本是否包含GUI相关代码
     */
    private static boolean containsGuiCode(String scriptContent) {
        if (scriptContent == null) {
            return false;
        }
        String[] guiKeywords = {
            "tkinter", "Tk(", "tk.Tk(",
            "PyQt5", "PyQt6", "PySide2", "PySide6",
            "wxPython", "wx.",
            "messagebox", "tkinter.messagebox",
            "tkinter.filedialog", "tkinter.simpledialog",
            "tkinter.colorchooser",
            "input(", "raw_input(",
            "dialog", "Dialog",
            "MessageBox", "QMessageBox",
            "QDialog", "QInputDialog",
            "QFileDialog", "QColorDialog",
            "font", "Font",
            "canvas", "Canvas",
            "turtle", "Turtle"
        };
        
        for (String keyword : guiKeywords) {
            if (scriptContent.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 执行Shell脚本
     */
    private static String executeShellScript(String scriptContent) throws Exception {
        String tempFile = createTempScriptFile(scriptContent, ".sh");
        ProcessBuilder processBuilder = new ProcessBuilder("sh", tempFile);
        return executeProcess(processBuilder);
    }

    /**
     * 执行CMD脚本
     */
    private static String executeCmdScript(String scriptContent) throws Exception {
        String tempFile = createTempScriptFile(scriptContent, ".bat");
        ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", tempFile);
        return executeProcess(processBuilder);
    }

    /**
     * 执行PowerShell脚本
     */
    private static String executePowerShellScript(String scriptContent) throws Exception {
        String tempFile = createTempScriptFile(scriptContent, ".ps1");
        ProcessBuilder processBuilder = new ProcessBuilder("powershell", "-File", tempFile);
        return executeProcess(processBuilder);
    }

    /**
     * 创建临时脚本文件
     */
    private static String createTempScriptFile(String content, String extension) throws Exception {
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("job_script_", extension);
        java.nio.file.Files.writeString(tempFile, content, java.nio.charset.StandardCharsets.UTF_8);
        tempFile.toFile().deleteOnExit();
        return tempFile.toString();
    }

    /**
     * 执行SQL脚本
     */
    private static String executeSqlScript(String scriptContent) throws Exception {
        throw new Exception(String.format("SQL脚本%s执行功能暂未实现，请使用数据库管理工具执行", scriptContent));
    }

    /**
     * 执行进程并获取输出
     */
    private static String executeProcess(ProcessBuilder processBuilder) throws Exception {
        return executeProcess(processBuilder, 60);
    }

    /**
     * 执行进程并获取输出（带超时控制）
     * 
     * @param processBuilder 进程构建器
     * @param timeoutSeconds 超时时间（秒）
     */
    private static String executeProcess(ProcessBuilder processBuilder, int timeoutSeconds) throws Exception {
        log.info("执行命令: {}, 超时时间: {}秒", String.join(" ", processBuilder.command()), timeoutSeconds);
        
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("进程执行超时（" + timeoutSeconds + "秒），已强制终止。输出：" + output);
        }
        
        int exitCode = process.exitValue();
        log.info("命令执行完成，退出码: {}, 输出: {}", exitCode, output);
        
        if (exitCode != 0) {
            throw new Exception("进程执行失败，退出码：" + exitCode + "，输出：" + output);
        }

        return output.toString().trim();
    }

    /**
     * 替换占位符
     */
    private static String replacePlaceholders(String text, SysJob sysJob) {
        if (MyStringUtils.isEmpty(text))
            return text;
        return text.replace("{jobId}", String.valueOf(sysJob.getJobId()))
                .replace("{jobName}", sysJob.getJobName())
                .replace("{jobGroup}", sysJob.getJobGroup());
    }

    private static Method findCompatibleMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes)
            throws NoSuchMethodException {
        try {
            return clazz.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            for (Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == paramTypes.length) {
                    Class<?>[] methodParamTypes = method.getParameterTypes();
                    boolean compatible = true;
                    for (int i = 0; i < paramTypes.length; i++) {
                        if (!isAssignable(paramTypes[i], methodParamTypes[i])) {
                            compatible = false;
                            break;
                        }
                    }
                    if (compatible) {
                        return method;
                    }
                }
            }
            throw e;
        }
    }

    private static boolean isAssignable(Class<?> from, Class<?> to) {
        if (to.isAssignableFrom(from)) {
            return true;
        }
        if (from == Integer.class && to == Long.class)
            return true;
        if (from == int.class && to == long.class)
            return true;
        if (from == Integer.class && to == long.class)
            return true;
        if (from == int.class && to == Long.class)
            return true;
        if (from == Float.class && to == Double.class)
            return true;
        if (from == float.class && to == double.class)
            return true;
        if (from == Float.class && to == double.class)
            return true;
        return from == float.class && to == Double.class;
    }

    private static Object[] convertParams(List<Object[]> methodParams, Class<?>[] targetTypes) {
        Object[] values = new Object[methodParams.size()];
        for (int i = 0; i < methodParams.size(); i++) {
            Object value = methodParams.get(i)[0];
            Class<?> targetType = targetTypes[i];
            values[i] = convertValue(value, targetType);
        }
        return values;
    }

    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null)
            return null;
        if (targetType.isAssignableFrom(value.getClass()))
            return value;
        if (value instanceof Number num) {
            if (targetType == long.class || targetType == Long.class)
                return num.longValue();
            if (targetType == double.class || targetType == Double.class)
                return num.doubleValue();
            if (targetType == float.class || targetType == Float.class)
                return num.floatValue();
            if (targetType == int.class || targetType == Integer.class)
                return num.intValue();
            if (targetType == short.class || targetType == Short.class)
                return num.shortValue();
            if (targetType == byte.class || targetType == Byte.class)
                return num.byteValue();
        }
        return value;
    }

    /**
     * 校验是否为为class包名
     * 
     * @param invokeTarget 名称
     * @return true是 false否
     */
    private static boolean isValidClassName(String invokeTarget) {
        return MyStringUtils.countMatches(invokeTarget, ".") > 1;
    }

    /**
     * 获取bean名称
     * 
     * @param invokeTarget 目标字符串
     * @return bean名称
     */
    private static String getBeanName(String invokeTarget) {
        String beanName = MyStringUtils.substringBefore(invokeTarget, "(");
        return MyStringUtils.substringBeforeLast(beanName, ".");
    }

    /**
     * 获取bean方法
     * 
     * @param invokeTarget 目标字符串
     * @return method方法
     */
    private static String getMethodName(String invokeTarget) {
        String methodName = MyStringUtils.substringBefore(invokeTarget, "(");
        return MyStringUtils.substringAfterLast(methodName, ".");
    }

    /**
     * 获取method方法参数相关列表
     * 
     * @param invokeTarget 目标字符串
     * @return method方法相关参数列表
     */
    private static List<Object[]> getMethodParams(String invokeTarget) {
        String methodStr = MyStringUtils.substringBetween(invokeTarget, "(", ")");
        if (MyStringUtils.isEmpty(methodStr)) {
            return null;
        }
        String[] methodParams = methodStr.split(",(?=([^\"']*[\"'][^\"']*[\"'])*[^\"']*$)");
        List<Object[]> clazz = new LinkedList<>();
        for (String methodParam : methodParams) {
            String str = MyStringUtils.trimToEmpty(methodParam);
            // String字符串类型，以'或"开头
            if (Strings.CS.startsWithAny(str, "'", "\"")) {
                clazz.add(new Object[] { MyStringUtils.substring(str, 1, str.length() - 1), String.class });
            }
            // boolean布尔类型，等于true或者false
            else if ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)) {
                clazz.add(new Object[] { Boolean.valueOf(str), Boolean.class });
            }
            // long长整形，以L结尾
            else if (MyStringUtils.endsWith(str, "L")) {
                clazz.add(new Object[] { Long.valueOf(MyStringUtils.substring(str, 0, str.length() - 1)), Long.class });
            }
            // double浮点类型，以D结尾
            else if (MyStringUtils.endsWith(str, "D")) {
                clazz.add(
                        new Object[] { Double.valueOf(MyStringUtils.substring(str, 0, str.length() - 1)), Double.class });
            }
            // 其他类型归类为整形
            else {
                if (str.contains(".")) {
                    clazz.add(new Object[] { Double.valueOf(str), Double.class });
                } else {
                    clazz.add(new Object[] { Integer.valueOf(str), Integer.class });
                }
            }
        }
        return clazz;
    }

    /**
     * 获取参数类型
     * 
     * @param methodParams 参数相关列表
     * @return 参数类型列表
     */
    private static Class<?>[] getMethodParamsType(List<Object[]> methodParams) {
        Class<?>[] classes = new Class<?>[methodParams.size()];
        int index = 0;
        for (Object[] os : methodParams) {
            classes[index] = (Class<?>) os[1];
            index++;
        }
        return classes;
    }
}