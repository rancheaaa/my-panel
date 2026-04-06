package com.cq.panel.admin.server.task.quartz;

import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.common.utils.spring.SpringUtils;
import com.cq.panel.admin.server.repository.domain.SysJob;
import com.cq.panel.common.loadbalancer.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

/**
 * 任务执行工具
 *
 * @author cq
 */
@Slf4j
public class JobInvokeUtil
{
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final HealthChecker HEALTH_CHECKER = HealthCheckerFactory.createDefault();

    /**
     * 执行方法
     *
     * @param sysJob 系统任务
     */
    public static String invokeMethod(SysJob sysJob) throws Exception
    {
        Integer jobType = sysJob.getJobType();
        if (jobType == null || jobType == 0) // 兼容原有逻辑
        {
            invokeTargetMethod(sysJob);
        }
        else if (jobType == 1)
        {
            invokeInternalMethod(sysJob);
        }
        else if (jobType == 2)
        {
            return invokeHttpInterface(sysJob);
        }
        else if (jobType == 3)
        {
            invokeScript(sysJob);
        }
        return null;
    }

    /**
     * 原有 invokeTarget 调度逻辑
     */
    private static void invokeTargetMethod(SysJob sysJob) throws Exception
    {
        String invokeTarget = sysJob.getInvokeTarget();
        String beanName = getBeanName(invokeTarget);
        String methodName = getMethodName(invokeTarget);
        List<Object[]> methodParams = getMethodParams(invokeTarget);

        if (!isValidClassName(beanName))
        {
            Object bean = SpringUtils.getBean(beanName);
            invokeMethod(bean, methodName, methodParams);
        }
        else
        {
            Object bean = Class.forName(beanName).getDeclaredConstructor().newInstance();
            invokeMethod(bean, methodName, methodParams);
        }
    }

    /**
     * 1-内置方法调度
     */
    private static void invokeInternalMethod(SysJob sysJob) throws Exception
    {
        String methodNameFull = sysJob.getMethodName();
        if (StringUtils.isEmpty(methodNameFull)) {
            throw new Exception("内置方法全限定名不能为空");
        }

        if (methodNameFull.contains("(") && methodNameFull.contains(")"))
        {
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
            // 先尝试从 Spring 容器获取，如果失败则尝试反射实例化
            String beanName = StringUtils.uncapitalize(className.substring(className.lastIndexOf(".") + 1));
            bean = SpringUtils.getBean(beanName);
        } catch (Exception e) {
            try {
                bean = SpringUtils.getBean(Class.forName(className));
            } catch (Exception e2) {
                bean = Class.forName(className).getDeclaredConstructor().newInstance();
            }
        }
        
        Method method = bean.getClass().getMethod(methodName);
        method.invoke(bean);
    }

    /**
     * 2-HTTP 接口调度
     */
    private static String invokeHttpInterface(SysJob sysJob) throws Exception
    {
        RestTemplate restTemplate = SpringUtils.getBean(RestTemplate.class);
        String httpUrl = sysJob.getHttpUrl();
        String methodStr = sysJob.getHttpMethod();
        String headersJson = sysJob.getHttpHeaders();
        String body = replacePlaceholders(sysJob.getHttpBody(), sysJob);
        String loadBalanceStrategy = sysJob.getLoadBalanceStrategy();

        HttpMethod httpMethod = HttpMethod.valueOf(methodStr.toUpperCase());
        HttpHeaders headers = new HttpHeaders();
        
        if (StringUtils.isNotEmpty(headersJson)) {
            Map<String, String> headerMap = objectMapper.readValue(headersJson, new TypeReference<>() {
            });
            headerMap.forEach(headers::add);
        }
        
        if (!headers.containsKey(HttpHeaders.CONTENT_TYPE) && StringUtils.isNotEmpty(body)) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        
        String selectedUrl;
        if (StringUtils.isNotEmpty(httpUrl) && httpUrl.contains(",")) {
            String[] urls = httpUrl.split(",");
            LoadBalancerAlgorithm algorithm = LoadBalancerAlgorithm.fromName(loadBalanceStrategy);
            LoadBalancer loadBalancer = LoadBalancerFactory.createLoadBalancer(algorithm);
            selectedUrl = choose(urls, loadBalancer);
            log.info("使用负载均衡策略：{}，从{}个URL中选择：{}", algorithm.getName(), urls.length, selectedUrl);
        } else {
            selectedUrl = replacePlaceholders(httpUrl, sysJob);
        }

        ResponseEntity<String> response = restTemplate.exchange(selectedUrl, httpMethod, entity, String.class);
        log.info("HTTP接口调用完成，sysJobId：{}，jobName：{}，jobGroup：{}，invokeTarget：{}，URL：{}，状态码：{}，响应内容：{}", sysJob.getJobId(), sysJob.getJobName(), sysJob.getJobGroup(), sysJob.getInvokeTarget(), selectedUrl, response.getStatusCode().value(), response.getBody());
        if (response.getStatusCode().isError()) {
            log.error("HTTP接口调用失败，sysJobId：{}，jobName：{}，jobGroup：{}，invokeTarget：{}，URL：{}，状态码：{}，响应内容：{}", sysJob.getJobId(), sysJob.getJobName(), sysJob.getJobGroup(), sysJob.getInvokeTarget(), selectedUrl, response.getStatusCode().value(), response.getBody());
            throw new Exception("HTTP 接口调用失败，sysJobId：{}，jobName：{}，jobGroup：{}，invokeTarget：{}，URL：{}，状态码：" + response.getStatusCode().value() + "，响应内容：" + response.getBody());
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
    private static void invokeScript(SysJob sysJob) throws Exception
    {
        String scriptName = sysJob.getScriptName();
        String scriptType = sysJob.getScriptType();
        String scriptContent = sysJob.getScriptContent();

        if (StringUtils.isEmpty(scriptName)) {
            throw new Exception("脚本名称不能为空");
        }
        if (StringUtils.isEmpty(scriptType)) {
            throw new Exception("脚本类型不能为空");
        }
        if (StringUtils.isEmpty(scriptContent)) {
            throw new Exception("脚本内容不能为空");
        }

        log.info("开始执行脚本，sysJobId：{}，jobName：{}，jobGroup：{}，脚本名称：{}，脚本类型：{}", 
                 sysJob.getJobId(), sysJob.getJobName(), sysJob.getJobGroup(), scriptName, scriptType);

        try {
            String output = executeScriptByType(scriptType, scriptContent);
            log.info("脚本执行成功，sysJobId：{}，jobName：{}，jobGroup：{}，脚本名称：{}，脚本类型：{}，执行输出：{}", 
                     sysJob.getJobId(), sysJob.getJobName(), sysJob.getJobGroup(), scriptName, scriptType, output);
        } catch (Exception e) {
            log.error("脚本执行失败，sysJobId：{}，jobName：{}，jobGroup：{}，脚本名称：{}，脚本类型：{}，错误信息：{}", 
                      sysJob.getJobId(), sysJob.getJobName(), sysJob.getJobGroup(), scriptName, scriptType, e.getMessage(), e);
            throw new Exception("脚本执行失败：" + e.getMessage(), e);
        }
    }

    /**
     * 根据脚本类型执行脚本
     */
    private static String executeScriptByType(String scriptType, String scriptContent) throws Exception
    {
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
    private static String executePythonScript(String scriptContent) throws Exception
    {
        String os = System.getProperty("os.name").toLowerCase();
        String pythonCommand = os.contains("win") ? "python" : "python3";
        
        String tempFile = createTempScriptFile(scriptContent, ".py");
        ProcessBuilder processBuilder = new ProcessBuilder(pythonCommand, tempFile);
        return executeProcess(processBuilder);
    }

    /**
     * 执行Shell脚本
     */
    private static String executeShellScript(String scriptContent) throws Exception
    {
        String tempFile = createTempScriptFile(scriptContent, ".sh");
        ProcessBuilder processBuilder = new ProcessBuilder("sh", tempFile);
        return executeProcess(processBuilder);
    }

    /**
     * 执行CMD脚本
     */
    private static String executeCmdScript(String scriptContent) throws Exception
    {
        String tempFile = createTempScriptFile(scriptContent, ".bat");
        ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", tempFile);
        return executeProcess(processBuilder);
    }

    /**
     * 执行PowerShell脚本
     */
    private static String executePowerShellScript(String scriptContent) throws Exception
    {
        String tempFile = createTempScriptFile(scriptContent, ".ps1");
        ProcessBuilder processBuilder = new ProcessBuilder("powershell", "-File", tempFile);
        return executeProcess(processBuilder);
    }

    /**
     * 创建临时脚本文件
     */
    private static String createTempScriptFile(String content, String extension) throws Exception
    {
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("job_script_", extension);
        java.nio.file.Files.writeString(tempFile, content, java.nio.charset.StandardCharsets.UTF_8);
        tempFile.toFile().deleteOnExit();
        return tempFile.toString();
    }

    /**
     * 执行SQL脚本
     */
    private static String executeSqlScript(String scriptContent) throws Exception
    {
        throw new Exception("SQL脚本执行功能暂未实现，请使用数据库管理工具执行");
    }

    /**
     * 执行进程并获取输出
     */
    private static String executeProcess(ProcessBuilder processBuilder) throws Exception
    {
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
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("进程执行失败，退出码：" + exitCode + "，输出：" + output);
        }
        
        return output.toString().trim();
    }

    /**
     * 替换占位符
     */
    private static String replacePlaceholders(String text, SysJob sysJob) {
        if (StringUtils.isEmpty(text)) return text;
        return text.replace("{jobId}", String.valueOf(sysJob.getJobId()))
                   .replace("{jobName}", sysJob.getJobName())
                   .replace("{jobGroup}", sysJob.getJobGroup());
    }

    /**
     * 调用任务方法
     *
     * @param bean 目标对象
     * @param methodName 方法名称
     * @param methodParams 方法参数
     */
    private static void invokeMethod(Object bean, String methodName, List<Object[]> methodParams)
            throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException
    {
        if (StringUtils.isNotNull(methodParams) && !methodParams.isEmpty())
        {
            Method method = bean.getClass().getMethod(methodName, getMethodParamsType(methodParams));
            method.invoke(bean, getMethodParamsValue(methodParams));
        }
        else
        {
            Method method = bean.getClass().getMethod(methodName);
            method.invoke(bean);
        }
    }

    /**
     * 校验是否为为class包名
     * 
     * @param invokeTarget 名称
     * @return true是 false否
     */
    public static boolean isValidClassName(String invokeTarget)
    {
        return StringUtils.countMatches(invokeTarget, ".") > 1;
    }

    /**
     * 获取bean名称
     * 
     * @param invokeTarget 目标字符串
     * @return bean名称
     */
    public static String getBeanName(String invokeTarget)
    {
        String beanName = StringUtils.substringBefore(invokeTarget, "(");
        return StringUtils.substringBeforeLast(beanName, ".");
    }

    /**
     * 获取bean方法
     * 
     * @param invokeTarget 目标字符串
     * @return method方法
     */
    public static String getMethodName(String invokeTarget)
    {
        String methodName = StringUtils.substringBefore(invokeTarget, "(");
        return StringUtils.substringAfterLast(methodName, ".");
    }

    /**
     * 获取method方法参数相关列表
     * 
     * @param invokeTarget 目标字符串
     * @return method方法相关参数列表
     */
    public static List<Object[]> getMethodParams(String invokeTarget)
    {
        String methodStr = StringUtils.substringBetween(invokeTarget, "(", ")");
        if (StringUtils.isEmpty(methodStr))
        {
            return null;
        }
        String[] methodParams = methodStr.split(",(?=([^\"']*[\"'][^\"']*[\"'])*[^\"']*$)");
        List<Object[]> clazz = new LinkedList<>();
        for (String methodParam : methodParams) {
            String str = StringUtils.trimToEmpty(methodParam);
            // String字符串类型，以'或"开头
            if (Strings.CS.startsWithAny(str, "'", "\"")) {
                clazz.add(new Object[]{StringUtils.substring(str, 1, str.length() - 1), String.class});
            }
            // boolean布尔类型，等于true或者false
            else if ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)) {
                clazz.add(new Object[]{Boolean.valueOf(str), Boolean.class});
            }
            // long长整形，以L结尾
            else if (StringUtils.endsWith(str, "L")) {
                clazz.add(new Object[]{Long.valueOf(StringUtils.substring(str, 0, str.length() - 1)), Long.class});
            }
            // double浮点类型，以D结尾
            else if (StringUtils.endsWith(str, "D")) {
                clazz.add(new Object[]{Double.valueOf(StringUtils.substring(str, 0, str.length() - 1)), Double.class});
            }
            // 其他类型归类为整形
            else {
                clazz.add(new Object[]{Integer.valueOf(str), Integer.class});
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
    public static Class<?>[] getMethodParamsType(List<Object[]> methodParams)
    {
        Class<?>[] classs = new Class<?>[methodParams.size()];
        int index = 0;
        for (Object[] os : methodParams)
        {
            classs[index] = (Class<?>) os[1];
            index++;
        }
        return classs;
    }

    /**
     * 获取参数值
     * 
     * @param methodParams 参数相关列表
     * @return 参数值列表
     */
    public static Object[] getMethodParamsValue(List<Object[]> methodParams)
    {
        Object[] classs = new Object[methodParams.size()];
        int index = 0;
        for (Object[] os : methodParams)
        {
            classs[index] = os[0];
            index++;
        }
        return classs;
    }
}